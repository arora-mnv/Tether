# Future Features (v2+)

Planned enhancements that are not yet implemented. Stubbed/placeholder systems have been removed from the source tree; this file tracks what was planned.

## OCR Receipt Scanning
- Directory `ocr/` was reserved for future receipt/image-based transaction extraction
- No implementation started

## Boot Receiver Auto-Sync
- `BootReceiver` was a stub registered for `BOOT_COMPLETED` with no implementation
- Intended to restart the notification listener service after device reboot
- Removed until sync infrastructure is finalized

## ML Category Patterns
- `CategoryPatternDao` / `CategoryPatternEntity` existed but were never registered in `AppDatabase`
- Planned for machine-learned category suggestions based on merchant patterns
- Models + training pipeline not started
