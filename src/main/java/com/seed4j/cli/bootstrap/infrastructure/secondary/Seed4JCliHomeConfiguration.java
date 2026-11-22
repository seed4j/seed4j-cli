package com.seed4j.cli.bootstrap.infrastructure.secondary;

import com.seed4j.cli.bootstrap.domain.Seed4JCliHome;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class Seed4JCliHomeConfiguration {

  @Bean
  Seed4JCliHome seed4jCliHome(@Value("${user.home}") String userHomePath) {
    return Seed4JCliHome.from(userHomePath);
  }
}
