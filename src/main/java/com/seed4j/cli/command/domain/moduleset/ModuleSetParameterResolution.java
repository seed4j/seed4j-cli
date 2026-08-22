package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;

sealed interface ModuleSetParameterResolution {
  record Resolved(ResolvedModuleSetParameter parameter) implements ModuleSetParameterResolution {
    public Resolved {
      Assert.notNull("parameter", parameter);
    }
  }

  record RequiredMissing(MissingRequiredModuleSetParameter parameter) implements ModuleSetParameterResolution {
    public RequiredMissing {
      Assert.notNull("parameter", parameter);
    }
  }

  record HistoryIncompatible(ModuleSetHistoryParameterTypeMismatch mismatch) implements ModuleSetParameterResolution {
    public HistoryIncompatible {
      Assert.notNull("mismatch", mismatch);
    }
  }

  record OptionalWithoutValue(ModuleSetPropertyKey key) implements ModuleSetParameterResolution {
    public OptionalWithoutValue {
      Assert.notNull("key", key);
    }
  }
}
