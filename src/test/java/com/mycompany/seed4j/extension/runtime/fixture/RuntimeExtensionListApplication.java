package com.mycompany.seed4j.extension.runtime.fixture;

import com.mycompany.seed4j.extension.runtime.main.list.RuntimeExtensionListOnlyModuleConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(RuntimeExtensionListOnlyModuleConfiguration.class)
public class RuntimeExtensionListApplication {}
