Add-Type -AssemblyName System.Runtime.WindowsRuntime

function AwaitWinRt($operation, $resultType) {
    $m = [System.WindowsRuntimeSystemExtensions].GetMethods() |
        Where-Object { $_.Name -eq 'AsTask' -and $_.IsGenericMethod -and $_.GetParameters().Count -eq 1 } |
        Select-Object -First 1
    $gm = $m.MakeGenericMethod($resultType)
    $task = $gm.Invoke($null, @($operation))
    $task.GetAwaiter().GetResult()
}

$selector = [Windows.Devices.Bluetooth.BluetoothLEDevice,Windows.Devices.Bluetooth,ContentType=WindowsRuntime]::GetDeviceSelector()
$op = [Windows.Devices.Enumeration.DeviceInformation,Windows.Devices.Enumeration,ContentType=WindowsRuntime]::FindAllAsync($selector)
$devices = AwaitWinRt $op ([Windows.Devices.Enumeration.DeviceInformationCollection])
$device = $devices | Where-Object { $_.Name -eq 'WH-BLE 106' } | Select-Object -First 1

if (-not $device) {
    Write-Output "DEVICE NOT FOUND"
    exit 0
}

Write-Output ("PAIRING CANPAIR {0}" -f $device.Pairing.CanPair)
Write-Output ("PAIRING ISPAIRED {0}" -f $device.Pairing.IsPaired)

$unpairResult = AwaitWinRt ($device.Pairing.UnpairAsync()) ([Windows.Devices.Enumeration.DeviceUnpairingResult])
Write-Output ("UNPAIR STATUS {0}" -f $unpairResult.Status)

Start-Sleep -Seconds 2

$devices = AwaitWinRt ([Windows.Devices.Enumeration.DeviceInformation,Windows.Devices.Enumeration,ContentType=WindowsRuntime]::FindAllAsync($selector)) ([Windows.Devices.Enumeration.DeviceInformationCollection])
$device = $devices | Where-Object { $_.Name -eq 'WH-BLE 106' } | Select-Object -First 1
if (-not $device) {
    Write-Output "DEVICE NOT FOUND AFTER UNPAIR"
    exit 0
}

$pairResult = AwaitWinRt ($device.Pairing.PairAsync()) ([Windows.Devices.Enumeration.DevicePairingResult])
Write-Output ("PAIR STATUS {0}" -f $pairResult.Status)
