'use strict';

import _ from 'lodash';
import angular from 'angular';

import AnalyzerRunController from './analyzer.run.controller';
import runAnalyzerModalTpl from './analyzer.run.modal.html';

export default class AnalyzersController {
  constructor($log, $state, $uibModal, AnalyzerService, NotificationService) {
    'ngInject';
    this.$log = $log;
    this.$state = $state;
    this.$uibModal = $uibModal;
    this.AnalyzerService = AnalyzerService;
    this.NotificationService = NotificationService;

    this.search = {
      description: '',
      dataTypeList: ''
    };

    this.datatypes = AnalyzerService.getTypes();

    this.$log.log(this.datatypes);
  }

  $onInit() {
    this.$log.debug('Called from analyzers controller');
  }

  filterByType(type) {
    if (this.search.dataTypeList === type) {
      this.search.dataTypeList = '';
    } else {
      this.search.dataTypeList = type;
    }
  }

  run(analyzer, dataType) {
    let modalInstance = this.$uibModal.open({
      animation: true,
      templateUrl: runAnalyzerModalTpl,
      controller: AnalyzerRunController,
      controllerAs: '$modal',
      size: 'lg',
      resolve: {
        initialData: () => ({
          analyzer: angular.copy(analyzer),
          dataType: angular.copy(dataType)
        })
      }
    });

    modalInstance.result
      .then(result => this.AnalyzerService.run(result.analyzer.id, result))
      .then(response => {
        this.$state.go('main.jobs');
        this.NotificationService.success(
          `${response.data.analyzerId} started successfully on ${
            response.data.data
          }`
        );
      })
      .catch(err => {
        if (err !== 'cancel') {
          this.NotificationService.error(
            'An error occurred: ' + err.statusText ||
              'An unexpected error occurred'
          );
        }
      });
  }
}
