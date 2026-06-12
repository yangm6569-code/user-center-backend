package cn.scysn

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class UserCenterApplication

fun main(args: Array<String>) {
    runApplication<UserCenterApplication>(*args)
}
