package com.suannai.netdisk;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan(basePackages = "com.suannai.netdisk.mapper")
public class NetDiskApplication {

    public static void main(String[] args) {
        SpringApplication.run(NetDiskApplication.class, args);
    }

}
