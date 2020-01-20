'use strict';

import _ from 'lodash/core';
import omit from 'lodash/omit';

export default class AnalyzerConfigFormController {
  constructor(Tlps, AnalyzerService) {
    'ngInject';

    this.AnalyzerService = AnalyzerService;
    this.Tlps = Tlps;
    this.rateUnits = ['Second', 'Minute', 'Hour', 'Day', 'Month'];
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
    const props = ['jobCache', 'jobTimeout'];

    this.applyConfig(omit(this.globalConfig.config, props));
    _.each(props, prop => {
      this.analyzer[prop] = this.globalConfig.config[prop];
    });
  }

  applyBaseConfig() {
    this.applyConfig(this.baseConfig.config);
  }
}