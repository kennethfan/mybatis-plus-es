package io.github.kennethfan.mpes.sample;

import io.github.kennethfan.mpes.annotation.EsMapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 示例应用：唯一需要的是 @EsMapperScan（对齐 MP 的 @MapperScan 体验）。
 */
@SpringBootApplication
@EsMapperScan("io.github.kennethfan.mpes.sample.mapper")
public class SampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleApplication.class, args);
    }
}
