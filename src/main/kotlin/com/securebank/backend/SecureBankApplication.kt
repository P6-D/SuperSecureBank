package com.securebank.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan

@SpringBootApplication
@ConfigurationPropertiesScan
class SecureBankApplication

fun main(args: Array<String>) {
    runApplication<SecureBankApplication>(*args)
}
