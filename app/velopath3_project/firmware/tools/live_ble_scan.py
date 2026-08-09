import asyncio

from bleak import BleakScanner


async def main() -> None:
    devices = await BleakScanner.discover(timeout=6.0, return_adv=True)
    for _, pair in devices.items():
        device, adv = pair
        name = adv.local_name or device.name or ""
        if "WH-BLE" in name or "VP-DBG" in name:
            print(f"{name} {device.address}")


if __name__ == "__main__":
    asyncio.run(main())
