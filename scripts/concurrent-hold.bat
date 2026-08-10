@echo off
rem Script: concurrent-hold.bat
rem Goal: Test concurrent seat hold HTTP requests against running Tomcat server
echo Testing concurrent seat hold requests on localhost:8080...
start /b curl -s -X POST "http://localhost:8080/Website-ban-ve-xem-phim/orders" -d "showtimeId=3&seatIds=1&paymentMethod=card"
start /b curl -s -X POST "http://localhost:8080/Website-ban-ve-xem-phim/orders" -d "showtimeId=3&seatIds=1&paymentMethod=card"
start /b curl -s -X POST "http://localhost:8080/Website-ban-ve-xem-phim/orders" -d "showtimeId=3&seatIds=1&paymentMethod=card"
start /b curl -s -X POST "http://localhost:8080/Website-ban-ve-xem-phim/orders" -d "showtimeId=3&seatIds=1&paymentMethod=card"
echo Concurrent hold requests dispatched.
