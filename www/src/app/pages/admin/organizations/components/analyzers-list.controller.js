'use strict';

import _ from 'lodash';

import AnalyzerEditController from './analyzer.edit.controller';
import editModalTpl from './analyzer.edit.modal.html';

export default class OrganizationAnalyzersController {
  constructor($log, $uibModal, OrganizationService) {
    'ngInject';

    this.$log = $log;
    this.$uibModal = $uibModal;
    this.OrganizationService = OrganizationService;
  }

  $onInit() {
    this.definitionsIds = _.difference(
      _.keys(this.analyzerDefinitions),
      _.map(this.analyzers, 'analyzerDefinitionId')
    ).sort();
  }

  openModal(mode, definition, analyzer) {
    let modal = this.$uibModal.open({
      animation: true,
      controller: AnalyzerEditController,
      controllerAs: '$modal',
      templateUrl: editModalTpl,
      size: 'lg',
      resolve: {
        definition: () => definition,
        analyzer: () => angular.copy(analyzer),
        mode: () => mode
      }
    });

    modal.result
      .then(response => {
        this.$log.log(response);
        if (mode === 'create') {
          return this.OrganizationService.enableAnalyzer(
            this.organization.id,
            definition.id,
            response
          );
        } else {
          return this.OrganizationService.updateAnalyzer(
            analyzer.id,
            _.pick(response, 'configuration', 'rate', 'rateUnit', 'name')
          );
        }
      })
      .then(() => this.reload())
      .catch(rejection => this.$log.log(rejection));
  }

  enable(analyzerId) {
    let definition = this.analyzerDefinitions[analyzerId];

    if (_.map(definition.configurationItems, 'required').indexOf(true) !== -1) {
      // The analyzer requires some configurations
      this.openModal('create', definition, {});
    } else {
      this.OrganizationService.enableAnalyzer(
        this.organization.id,
        analyzerId,
        {
          name: analyzerId
        }
      ).then(response => {
        this.$log.log(`Analyzer ${analyzerId} enabled`, response);
        this.reload();
      });
    }
  }

  disable(analyzerId) {
    this.OrganizationService.disableAnalyzer(analyzerId).then(response => {
      this.$log.log(`Analyzer ${analyzerId} disabled`, response);
      this.reload();
    });
  }

  reload() {
    this.OrganizationService.analyzers(this.organization.id).then(analyzers => {
      this.analyzers = analyzers;
      this.$onInit();
    });
  }
}
