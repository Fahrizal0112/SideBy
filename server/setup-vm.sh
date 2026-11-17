#!/usr/bin/env bash
# Pasang server perantara Sideby di VM Ubuntu/Debian: ntfy (menyimpan pesan & foto lama) + HTTPS.
# HTTPS lewat nginx yang sudah ada di VM (tanpa mengubah situs yang berjalan), atau Caddy bila port 80/443 masih kosong.
#
# Pemakaian di VM:   sudo bash setup-vm.sh            (domain dibuat dari IP publik, mis. 1-2-3-4.sslip.io)
#                    sudo bash setup-vm.sh chat.contoh.com   (kalau punya domain sendiri yang sudah mengarah ke VM)
#
# Aman dijalankan ulang. Yang dibutuhkan: port 80 dan 443 terbuka (di firewall / security group penyedia VM).
# Ringan: ntfy dan Caddy masing-masing hanya butuh puluhan MB RAM; skrip membatasi pemakaian memorinya
# dan menyesuaikan batas penyimpanan dengan ruang disk yang ada.
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
  echo "Jalankan dengan sudo:  sudo bash $0" >&2
  exit 1
fi

NTFY_VERSION="${NTFY_VERSION:-2.38.1}"
RETENTION="43800h"   # lama penyimpanan pesan dan lampiran: 5 tahun
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "==> Kondisi VM"
MEM_MB="$(awk '/MemTotal/ {printf "%d", $2/1024}' /proc/meminfo)"
MEM_FREE_MB="$(awk '/MemAvailable/ {printf "%d", $2/1024}' /proc/meminfo)"
CPUS="$(nproc)"
mkdir -p /var/cache/ntfy
AVAIL_GB="$(df -BG --output=avail /var/cache | tail -1 | tr -dc '0-9')"
echo "    RAM: ${MEM_MB} MB (tersedia ${MEM_FREE_MB} MB) | CPU: ${CPUS} inti | Disk kosong: ${AVAIL_GB} GB"
if [ "$MEM_MB" -lt 600 ]; then
  echo "    PERINGATAN: RAM kecil. Server tetap bisa jalan, tapi sebaiknya ada swap." >&2
fi
if [ "$AVAIL_GB" -lt 5 ]; then
  echo "Disk kosong kurang dari 5 GB; tidak aman untuk menyimpan foto. Hentikan." >&2
  exit 1
fi
# Batas lampiran = 40% dari disk kosong, paling banyak 20 GB; per pengguna sepertiganya.
TOTAL_GB=$(( AVAIL_GB * 40 / 100 )); [ "$TOTAL_GB" -gt 20 ] && TOTAL_GB=20; [ "$TOTAL_GB" -lt 2 ] && TOTAL_GB=2
VISITOR_GB=$(( TOTAL_GB / 3 )); [ "$VISITOR_GB" -lt 1 ] && VISITOR_GB=1
echo "    Batas penyimpanan lampiran: ${TOTAL_GB} GB total, ${VISITOR_GB} GB per pengguna"

DOMAIN="${1:-}"
if [ -z "$DOMAIN" ]; then
  PUBIP="$(curl -4 -fsS --max-time 10 https://api.ipify.org || curl -4 -fsS --max-time 10 https://ifconfig.me)"
  DOMAIN="${PUBIP//./-}.sslip.io"
fi
echo "==> Domain: $DOMAIN"

export DEBIAN_FRONTEND=noninteractive
ARCH="$(dpkg --print-architecture)"
apt-get update -y
apt-get install -y curl gnupg ca-certificates apt-transport-https

echo "==> Memasang ntfy $NTFY_VERSION"
if ! command -v ntfy >/dev/null 2>&1; then
  DEB=""
  # 1) Berkas .deb yang sudah ada di folder ini / home (mis. disalin dari Mac lewat scp).
  for f in "$HERE"/ntfy_*_linux_"$ARCH".deb /home/*/ntfy_*_linux_"$ARCH".deb /root/ntfy_*_linux_"$ARCH".deb; do
    [ -f "$f" ] && { DEB="$f"; break; }
  done
  # 2) Unduh dari GitHub Releases.
  if [ -z "$DEB" ]; then
    URL="https://github.com/binwiederhier/ntfy/releases/download/v${NTFY_VERSION}/ntfy_${NTFY_VERSION}_linux_${ARCH}.deb"
    if curl -fsSL --retry 3 --connect-timeout 15 --max-time 180 -o /tmp/ntfy.deb "$URL"; then
      DEB=/tmp/ntfy.deb
    fi
  fi
  if [ -z "$DEB" ]; then
    echo "Tidak bisa mengunduh ntfy dari GitHub." >&2
    echo "Unduh manual di Mac:  https://github.com/binwiederhier/ntfy/releases/tag/v${NTFY_VERSION}" >&2
    echo "  (berkas ntfy_${NTFY_VERSION}_linux_${ARCH}.deb), salin ke VM:  scp berkas.deb ubuntu@IP-VM:~" >&2
    echo "  lalu jalankan skrip ini lagi." >&2
    exit 1
  fi
  apt-get install -y "$DEB"
fi

echo "==> Menulis konfigurasi ntfy"
[ -f /etc/ntfy/server.yml ] && [ ! -f /etc/ntfy/server.yml.asli ] && cp /etc/ntfy/server.yml /etc/ntfy/server.yml.asli
mkdir -p /var/cache/ntfy/attachments
id ntfy >/dev/null 2>&1 && chown -R ntfy:ntfy /var/cache/ntfy
cat > /etc/ntfy/server.yml <<YML
# Dibuat oleh server/setup-vm.sh (Sideby)
base-url: "https://$DOMAIN"
listen-http: "127.0.0.1:2586"
behind-proxy: true

# Pesan disimpan lama supaya HP yang offline berhari-hari tetap menerima semuanya begitu tersambung.
cache-file: "/var/cache/ntfy/cache.db"
cache-duration: "$RETENTION"

# Foto dan voice note.
attachment-cache-dir: "/var/cache/ntfy/attachments"
attachment-expiry-duration: "$RETENTION"
attachment-file-size-limit: "30M"
attachment-total-size-limit: "${TOTAL_GB}G"
visitor-attachment-total-size-limit: "${VISITOR_GB}G"
visitor-attachment-daily-bandwidth-limit: "2G"

# Aplikasi memakai koneksi terbuka + beberapa permintaan sekaligus; batas bawaan terlalu ketat.
visitor-request-limit-burst: 600
visitor-request-limit-replenish: "1s"
visitor-subscription-limit: 60
keepalive-interval: "45s"
YML

# Jaga agar ringan: memori Go dibatasi lunak, dan prioritas CPU sedikit di bawah layanan lain di VM.
mkdir -p /etc/systemd/system/ntfy.service.d
cat > /etc/systemd/system/ntfy.service.d/ringan.conf <<'CONF'
[Service]
Environment=GOMEMLIMIT=120MiB
Nice=5
CONF

systemctl daemon-reload
systemctl enable ntfy >/dev/null 2>&1 || true
systemctl restart ntfy
sleep 2
if ! curl -fsS --max-time 5 http://127.0.0.1:2586/v1/health >/dev/null; then
  echo "ntfy tidak mau jalan. Lihat penyebabnya:  journalctl -u ntfy -n 50 --no-pager" >&2
  exit 1
fi
echo "    ntfy berjalan."

# ---------------------------------------------------------------------------
# Pintu masuk HTTPS. Port 80/443 yang sudah dipakai nginx TIDAK direbut: nginx yang ada dipakai
# untuk meneruskan ke ntfy lewat satu berkas tambahan. Bila port masih kosong, dipasang Caddy.
# ---------------------------------------------------------------------------
LISTENERS="$(ss -ltnpH '( sport = :80 or sport = :443 )' 2>/dev/null || true)"
if [ -n "$LISTENERS" ] && echo "$LISTENERS" | grep -q nginx; then
  PROXY=nginx
elif [ -n "$LISTENERS" ]; then
  echo "Port 80/443 dipakai program selain nginx:" >&2
  echo "$LISTENERS" >&2
  echo "Skrip tidak mengambil alih port itu. Hentikan dan hubungi pembuat skrip." >&2
  exit 1
else
  PROXY=caddy
fi
echo "==> Pintu masuk HTTPS: $PROXY"

# Caddy bawaan percobaan sebelumnya (gagal karena port dipakai nginx) dimatikan agar tidak mengulang error.
if [ "$PROXY" = nginx ] && command -v caddy >/dev/null 2>&1; then
  systemctl disable --now caddy >/dev/null 2>&1 || true
fi

if [ "$PROXY" = nginx ]; then
  [ -f /etc/nginx/nginx.conf ] || { echo "nginx terdeteksi tetapi /etc/nginx/nginx.conf tidak ada (nginx buatan panel?). Hentikan." >&2; exit 1; }
  apt-get install -y certbot
  mkdir -p /var/www/sideby-acme
  CONF=/etc/nginx/conf.d/sideby-ntfy.conf

  rollback() {
    echo "Mengembalikan nginx ke keadaan semula." >&2
    rm -f "$CONF"
    nginx -t >/dev/null 2>&1 && systemctl reload nginx || true
  }

  # Tahap 1: hanya port 80, untuk validasi sertifikat Let's Encrypt.
  cat > "$CONF" <<'NGX'
server {
    listen 80;
    listen [::]:80;
    server_name __DOMAIN__;
    location /.well-known/acme-challenge/ { root /var/www/sideby-acme; }
    location / { return 404; }
}
NGX
  sed -i "s/__DOMAIN__/$DOMAIN/g" "$CONF"
  if ! nginx -t; then echo "Konfigurasi nginx ditolak." >&2; rollback; exit 1; fi
  if ! nginx -T 2>/dev/null | grep -q "sideby-ntfy.conf"; then
    echo "nginx-mu tidak memuat folder /etc/nginx/conf.d. Hentikan, tidak ada yang diubah." >&2; rollback; exit 1
  fi
  systemctl reload nginx

  if [ ! -f "/etc/letsencrypt/live/$DOMAIN/fullchain.pem" ]; then
    if ! certbot certonly --webroot -w /var/www/sideby-acme -d "$DOMAIN" \
         --non-interactive --agree-tos --register-unsafely-without-email \
         --deploy-hook "systemctl reload nginx"; then
      echo "Sertifikat HTTPS gagal dibuat. Pastikan port 80 terbuka dari internet (security group)." >&2
      rollback; exit 1
    fi
  fi

  # Tahap 2: HTTPS penuh, diteruskan ke ntfy.
  cat > "$CONF" <<'NGX'
server {
    listen 80;
    listen [::]:80;
    server_name __DOMAIN__;
    location /.well-known/acme-challenge/ { root /var/www/sideby-acme; }
    location / { return 301 https://$host$request_uri; }
}
server {
    listen 443 ssl;
    listen [::]:443 ssl;
    server_name __DOMAIN__;
    ssl_certificate /etc/letsencrypt/live/__DOMAIN__/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/__DOMAIN__/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    client_max_body_size 35m;
    location / {
        proxy_pass http://127.0.0.1:2586;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header Connection "";
        # Data chat dikirim langsung tanpa ditahan, dan koneksi terbuka lama diizinkan.
        proxy_buffering off;
        proxy_request_buffering off;
        proxy_read_timeout 3h;
        proxy_send_timeout 3h;
    }
}
NGX
  sed -i "s/__DOMAIN__/$DOMAIN/g" "$CONF"
  if ! nginx -t; then echo "Konfigurasi nginx tahap 2 ditolak." >&2; rollback; exit 1; fi
  systemctl reload nginx
else
  echo "==> Memasang Caddy (HTTPS otomatis)"
  if ! command -v caddy >/dev/null 2>&1; then
    # Paket bawaan Ubuntu dulu (memakai mirror distro, paling kecil kemungkinan gagal); lalu gudang resmi Caddy.
    if ! apt-get install -y caddy; then
      curl -fsSL https://dl.cloudsmith.io/public/caddy/stable/gpg.key | gpg --dearmor --yes -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
      curl -fsSL https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt > /etc/apt/sources.list.d/caddy-stable.list
      apt-get update -y
      apt-get install -y caddy
    fi
  fi
  cat > /etc/caddy/Caddyfile <<CADDY
$DOMAIN {
    reverse_proxy 127.0.0.1:2586 {
        # Kirim data langsung tanpa ditahan, penting untuk koneksi chat yang terbuka terus.
        flush_interval -1
    }
}
CADDY
  mkdir -p /etc/systemd/system/caddy.service.d
  cat > /etc/systemd/system/caddy.service.d/ringan.conf <<'CONF'
[Service]
Environment=GOMEMLIMIT=80MiB
Nice=5
CONF
  systemctl daemon-reload
  systemctl enable caddy >/dev/null 2>&1 || true
  systemctl restart caddy
fi

if command -v ufw >/dev/null 2>&1 && ufw status | grep -q "Status: active"; then
  echo "==> Membuka port 80 dan 443 di ufw"
  ufw allow 80/tcp >/dev/null
  ufw allow 443/tcp >/dev/null
fi

echo "==> Menunggu sertifikat HTTPS (maksimal 90 detik)"
OK=0
for _ in $(seq 1 30); do
  if curl -fsS --max-time 8 "https://$DOMAIN/v1/health" 2>/dev/null | grep -q '"healthy":true'; then OK=1; break; fi
  sleep 3
done

echo
echo "Pemakaian sekarang:"
ps -o pid,rss,comm -C ntfy 2>/dev/null | awk 'NR==1{print "   " $0} NR>1{printf "   %s  %.0f MB  %s\n", $1, $2/1024, $3}'
if [ "$OK" -eq 1 ]; then
  echo "SELESAI. Server perantara aktif di https://$DOMAIN"
else
  echo "ntfy sudah jalan, tetapi https://$DOMAIN belum bisa dijangkau dari luar." >&2
  echo "Penyebab paling umum: port 80 dan 443 belum dibuka di firewall / security group penyedia VM." >&2
  echo "Setelah dibuka, jalankan:  curl https://$DOMAIN/v1/health" >&2
  echo "Catatan: journalctl -u ntfy -n 30 --no-pager   dan   sudo nginx -t" >&2
  exit 1
fi
