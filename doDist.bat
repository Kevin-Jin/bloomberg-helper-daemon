CALL gradlew fatJar
IF NOT EXIST dist (mkdir dist)
move build\libs\bloomberg-helper-daemon-all-1.0.jar dist\bloomberg-helper-daemon.jar
java -jar dist/bloomberg-helper-daemon.jar
pause