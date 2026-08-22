package com.seed4j.cli.command.domain.moduleset;

public sealed interface ModuleSetParameterValue
  permits ModuleSetStringParameterValue, ModuleSetIntegerParameterValue, ModuleSetBooleanParameterValue
{
  ModuleSetPropertyType type();
}
