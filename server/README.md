# Server perantara Sideby (VM sendiri)

Aplikasi memakai server perantara untuk meneruskan chat, foto, voice note, dan status antar dua HP.
`setup-vm.sh` memasang server itu di VM Ubuntu/Debian milik sendiri:

- **ntfy**: server perantara yang sama dengan yang dipakai aplikasi selama ini, tetapi menyimpan pesan dan
  lampiran 5 tahun (server publik hanya ~12 jam / ~3 jam), jadi HP yang offline lama tetap menerima semuanya.
- **HTTPS**: lewat nginx yang sudah ada di VM (satu berkas tambahan, situs lain tidak disentuh) atau Caddy bila port 80/443 masih kosong. Tanpa domain sendiri, dipakai nama `IP-DENGAN-STRIP.sslip.io`.

Aplikasi tetap memakai ntfy.sh dan ntfy.envs.net sebagai cadangan, jadi bila VM mati aplikasi tetap jalan.

## Memasang

1. Buka port **80** dan **443** di firewall / security group penyedia VM.
2. Salin skrip ke VM, lalu jalankan:

```bash
scp server/setup-vm.sh ubuntu@IP-VM:~
ssh ubuntu@IP-VM
sudo bash setup-vm.sh
```

Skrip aman dijalankan ulang. Di akhir ia memeriksa `https://<domain>/v1/health`.

## Perawatan

- Log: `journalctl -u ntfy -n 50 --no-pager` dan `journalctl -u caddy -n 50 --no-pager`
- Data ada di `/var/cache/ntfy` (`cache.db` untuk pesan, `attachments/` untuk foto & voice note). Cadangkan folder ini.
- Alamat server ada di `CloudSyncManager.kt` (`OWN_RELAY`). Bila domain berubah, ganti di sana lalu rilis ulang.
