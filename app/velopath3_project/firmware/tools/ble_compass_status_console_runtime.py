import asyncio
import sys
from pathlib import Path
sys.path.insert(0, str(Path(r"C:\Users\vladimir.DESKTOP-L4RFPI0\AndroidStudioProjects\velopath3\firmware\tools")))
from ble_stable_terminal import open_session, probe_terminal, send_line, wait_for_prompt, prepare_windows_ble

async def main():
    prepare_windows_ble()
    while True:
        try:
            client, rx = await open_session()
            try:
                ok = await probe_terminal(client, rx)
                if not ok:
                    print("STREAM WAITING: probe failed, reconnecting...", flush=True)
                    await client.disconnect()
                    await asyncio.sleep(1.0)
                    continue
                print("COMPASS STATUS STREAM CONNECTED", flush=True)
                await send_line(client, "compass stream 250")
                await wait_for_prompt(rx, timeout_s=1.5)
                await send_line(client, "compass stream on")
                await wait_for_prompt(rx, timeout_s=1.5)
                print("Listening for compass stream. Press Ctrl+C to exit.", flush=True)
                while True:
                    await asyncio.sleep(1.0)
            finally:
                try:
                    await client.disconnect()
                except Exception:
                    pass
        except KeyboardInterrupt:
            raise
        except Exception as exc:
            print(f"STREAM WAITING: {exc}", flush=True)
            await asyncio.sleep(2.0)

asyncio.run(main())
