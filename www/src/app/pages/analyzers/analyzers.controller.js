'use strict';

import _ from 'lodash';
import angular from 'angular';

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

    // TODO remove this once the analyzers start including all the props
    // this.analyzers.map(analyzer =>
    //   _.extend(analyzer, this.definitions[analyzer.analyzerDefinitionId])
    // );
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
      templateUrl: 'views/analyzers.form.html',
      controller: 'AnalyzerFormCtrl',
      controllerAs: 'vm',
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
        this.$state.go('app.jobs');
        this.NotificationService.success(
          `${response.data.analyzerId} started successfully on ${
            response.data.artifact.data
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
