# FastScan backups

The backup job creates a verified PostgreSQL custom-format dump without stopping
the API. By default, dumps are stored in `/var/backups/fastscan` for seven days.
This protects against accidental database changes, but a copy on the same VPS
does not protect against loss of that VPS.

## Manual backup

From the repository directory on the VPS:

```bash
sudo FASTSCAN_PROJECT_DIR=/opt/fastscan ./deploy/backup.sh
```

Optional settings:

```bash
FASTSCAN_BACKUP_DIR=/mnt/backups
FASTSCAN_BACKUP_RETENTION_DAYS=14
FASTSCAN_POSTGRES_SERVICE=postgres
```

## Daily systemd timer

After cloning the project to `/opt/fastscan`:

```bash
sudo chmod +x /opt/fastscan/deploy/backup.sh /opt/fastscan/deploy/restore.sh
sudo cp /opt/fastscan/deploy/fastscan-backup.service /etc/systemd/system/
sudo cp /opt/fastscan/deploy/fastscan-backup.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now fastscan-backup.timer
sudo systemctl start fastscan-backup.service
```

Check the schedule and logs:

```bash
systemctl list-timers fastscan-backup.timer
sudo systemctl status fastscan-backup.service
sudo journalctl -u fastscan-backup.service
sudo ls -lh /var/backups/fastscan
```

## Restore

Restoration replaces the current database contents and briefly stops the API:

```bash
sudo /opt/fastscan/deploy/restore.sh \
  /var/backups/fastscan/fastscan-YYYY-MM-DDTHH-MM-SSZ.dump --confirm
```

Always make a fresh backup before a restore. Test restoration on a non-production
database before relying on the backup procedure.
