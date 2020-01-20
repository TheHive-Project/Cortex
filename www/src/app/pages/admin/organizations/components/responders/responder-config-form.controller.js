'use strict';

import _ from 'lodash/core';
import omit from 'lodash/omit';

export default class ResponderConfigFormController {
  constructor(Tlps, ResponderService) {
    'ngInject';

    this.ResponderService = ResponderService;
    this.Tlps = Tlps;
    this.rateUnits = ['Second', 'Minute', 'Hour', 'Day', 'Month'];
  }

  applyConfig(config) {
    _.forEach(
      _.keys(config),
      k => (this.responder.configuration[k] = config[k])
    );
  }

  applyGlobalConfig() {
    const props = ['jobTimeout'];

    this.applyConfig(omit(this.globalConfig.config, props));
    _.each(props, prop => {
      this.responder[prop] = this.globalConfig.config[prop];
    });
  }

  applyBaseConfig() {
    this.applyConfig(this.baseConfig.config);
  }
}