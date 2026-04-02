<#
Performs a safe clean of the Gradle state for local development.
Usage (PowerShell):
    .\scripts\clean-build.ps1
#>

Write-Output "Stopping Gradle daemons..."
.\gradlew.bat --stop

Write-Output "Cleaning project and refreshing dependencies..."
.\gradlew.bat clean --no-daemon

Write-Output "Refreshing dependencies and assembling debug APK..."
.\gradlew.bat :app:assembleDebug --refresh-dependencies --no-daemon

Write-Output "Done. If you still see build issues, try removing .gradle/ and .idea/ caches and re-run this script."

