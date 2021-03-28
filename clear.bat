# Удаление сгенерированного запускаемым сервером контента в рут проекта При ошибочной настройке текущих каталогов
del server.properties
del eula.txt
del banned-ips.json
del banned-players.json
del ops.json
del usercache.json
del whitelist.json
remdir /S /Q config
remdir /S /Q world
rmdir /S /Q mods
rmdir /S /Q logs
pause
#@SuppressWarnings("unchecked")