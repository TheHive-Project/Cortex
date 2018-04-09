'use strict';

import _ from 'lodash/core';

export default class AnalyzerConfigFormController {
  constructor($log, Tlps, AnalyzerService) {
    'ngInject';

    this.AnalyzerService = AnalyzerService;
    this.Tlps = Tlps;
    this.rateUnits = ['Day', 'Month'];
  }

  $onInit() {
    this.useGlobalCache =
      this.analyzer.jobCache === null || this.analyzer.jobCache === undefined;
  }

  applyConfig(config) {
    _.forEach(
      _.keys(config),
      k => (this.analyzer.configuration[k] = config[k])
    );
  }

  applyGlobalConfig() {
    this.applyConfig(this.globalConfig.config);
  }

  applyBaseConfig() {
    this.applyConfig(this.baseConfig.config);
  }
}
