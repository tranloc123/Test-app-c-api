@echo off
setlocal
where adb >nul 2>nul
if errorlevel 1 (
  echo [SCBD] Khong tim thay adb.exe.
  echo Cai Android Platform Tools va them adb vao PATH, sau do chay lai file nay.
  pause
  exit /b 1
)

echo [SCBD] Kiem tra ROG Phone 6...
adb get-state >nul 2>nul
if errorlevel 1 (
  echo [SCBD] Chua thay thiet bi ADB. Cam USB, bat USB debugging va chap nhan RSA tren dien thoai.
  adb devices
  pause
  exit /b 1
)

echo [SCBD] Forward PC 127.0.0.1:8797 -^> controller Termux tren dien thoai...
adb forward tcp:8797 tcp:8797 >nul
if errorlevel 1 (
  echo [SCBD] ADB forward that bai.
  pause
  exit /b 1
)

echo [SCBD] OK. Mo trang dieu khien tren PC.
start "" "http://127.0.0.1:8797/"
echo.
echo Giu controller V0.6.0R1 dang chay trong Termux tren ROG Phone 6.
echo APK/PPSSPP van chay tren dien thoai; PC chi la man hinh dieu khien.
pause
