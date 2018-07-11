'use strict';

import _ from 'lodash/core';

export default class ResponderConfigFormController {
  constructor($log, Tlps, ResponderService) {
    'ngInject';

    this.ResponderService = ResponderService;
    this.Tlps = Tlps;
    this.rateUnits = ['Day', 'Month'];
  }

  applyConfig(config) {
    _.forEach(
      _.keys(config),
      k => (this.responder.configuration[k] = config[k])
    );
  }

  applyGlobalConfig() {
    this.applyConfig(this.globalConfig.config);
  }

  applyBaseConfig() {
    this.applyConfig(this.baseConfig.config);
  }
}