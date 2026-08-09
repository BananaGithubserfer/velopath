import asyncio

from ble_stable_terminal import open_session, prepare_windows_ble


async def main() -> None:
    prepare_windows_ble()
    while True:
        session = None
        try:
            session, _ = await open_session()
            print("COMPASS STREAM CONNECTED")
            print("Listening for BLE notify data. Press Ctrl+C to exit.\n")
            while True:
                await asyncio.sleep(1.0)
        except KeyboardInterrupt:
            if session is not None:
                await session.disconnect()
            raise
        except Exception as exc:  # noqa: BLE001
            print(f"STREAM WAITING: {exc}")
            if session is not None:
                try:
                    await session.disconnect()
                except Exception:  # noqa: BLE001
                    pass
            await asyncio.sleep(2.0)


if __name__ == "__main__":
    asyncio.run(main())
