'use strict';

import _ from 'lodash/core';

export default class ResponderEditController {
  constructor(
    $log,
    $uibModalInstance,
    definition,
    globalConfig,
    baseConfig,
    configuration,
    responder,
    mode
  ) {
    'ngInject';

    this.$log = $log;
    this.$uibModalInstance = $uibModalInstance;
    this.mode = mode;
    this.definition = definition;
    this.globalConfig = globalConfig;
    this.baseConfig = baseConfig;
    this.configuration = configuration;
    this.responder = responder;
  }

  $onInit() {
    if (_.isEmpty(this.responder)) {
      let responder = {
        name: this.definition.id,
        configuration: {},
        rate: undefined,
        rateUnit: undefined,
        jobTimeout: undefined
      };

      _.forEach(this.definition.configurationItems, item => {
        const property = item.name,
          configValue = (this.configuration.config || {})[property];

          if(configValue !== undefined) {
            responder.configuration[property] = configValue;
          } else if (item.defaultValue !== undefined) {
            responder.configuration[property] = item.defaultValue;
          } else {
            responder.configuration[property] = item.multi ? [undefined] : undefined;
          }
      });

      // Handle TLP default config
      const globalConfig = [
        'proxy_http',
        'proxy_https',
        'cacerts',
        'jobTimeout'
      ];
      _.forEach(globalConfig, cnf => {
        if (responder.configuration[cnf] === undefined) {
          responder.configuration[cnf] =
            this.configuration.config[cnf] !== undefined ?
            this.configuration.config[cnf] :
            undefined;
        }
      });

      if (responder.configuration.check_tlp === undefined) {
        responder.configuration.check_tlp = true;
      }
      if (responder.configuration.max_tlp === undefined) {
        responder.configuration.max_tlp = 2;
      }
      if (responder.configuration.check_pap === undefined) {
        responder.configuration.check_pap = true;
      }
      if (responder.configuration.max_pap === undefined) {
        responder.configuration.max_pap = 2;
      }

      if (responder.jobTimeout === undefined) {
        responder.jobTimeout = this.globalConfig.config.jobTimeout;
      }

      this.responder = responder;
    }
  }

  save() {
    this.$uibModalInstance.close(this.responder);
  }
  cancel() {
    this.$uibModalInstance.dismiss('cancel');
  }
}