'use strict';
import _ from 'lodash/core';

import ConfigurationEditController from '../config.edit.controller';
import configurationEditTpl from '../config.edit.modal.html';

export default class OrganizationConfigsController {
  constructor($log, $uibModal, AnalyzerService, NotificationService) {
    'ngInject';

    this.$log = $log;
    this.$uibModal = $uibModal;
    this.AnalyzerService = AnalyzerService;
    this.NotificationService = NotificationService;

    this.state = {
      filter: ''
    };
  }

  isSet(config) {
    return _.indexOf([undefined, null, ''], config) === -1;
  }

  edit(config) {
    let modal = this.$uibModal.open({
      controller: ConfigurationEditController,
      templateUrl: configurationEditTpl,
      controllerAs: '$modal',
      size: 'lg',
      resolve: {
        configuration: () => {
          let conf = angular.copy(config);

          _.forEach(conf.configurationItems, item => {
            if(conf.config[item.name] === undefined) {
              
              if (item.defaultValue !== undefined) {
                conf.config[item.name] = item.defaultValue;  
              } else {
                conf.config[item.name] = item.multi ? [undefined] : undefined;
              }
            }
          });

          return conf;
        }
      }
    });

    modal.result
      .then(configuration =>
        this.AnalyzerService.saveConfiguration(config.name, {
          config: configuration
        })
      )
      .then(() => this.AnalyzerService.configurations())
      .then(configs => (this.configurations = configs))
      .then(() =>
        this.NotificationService.success(
          `Configuration ${config.name} updated successfully`
        )
      )
      .catch(err => {
        if (!_.isString(err)) {
          this.NotificationService.error(
            `Failed to update configuration ${config.name}`
          );
        }
      });
  }
}