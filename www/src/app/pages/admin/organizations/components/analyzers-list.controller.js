'use strict';

import _ from 'lodash';

import AnalyzerEditController from './analyzer.edit.controller';
import editModalTpl from './analyzer.edit.modal.html';

export default class OrganizationAnalyzersController {
  constructor(
    $log,
    $uibModal,
    OrganizationService,
    ModalService,
    NotificationService
  ) {
    'ngInject';

    this.$log = $log;
    this.$uibModal = $uibModal;
    this.OrganizationService = OrganizationService;
    this.ModalService = ModalService;
    this.NotificationService = NotificationService;
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

    return modal.result
      .then(response => {
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
      this.openModal('create', definition, {}).then(() => {
        this.NotificationService.success('Analyzer enabled successfully');
      });
    } else {
      this.OrganizationService.enableAnalyzer(
        this.organization.id,
        analyzerId,
        {
          name: analyzerId
        }
      ).then(() => {
        this.NotificationService.success('Analyzer enabled successfully');
        this.reload();
      });
    }
  }

  disable(analyzerId) {
    let modalInstance = this.ModalService.confirm(
      'Disable analyzer',
      'Are you sure you want to disable this analyzer? The corresponding configuration will be lost.',
      {
        flavor: 'danger',
        okText: 'Yes, disable it'
      }
    );

    modalInstance.result
      .then(() => this.OrganizationService.disableAnalyzer(analyzerId))
      .then(() => {
        this.reload();
        this.NotificationService.success('Analyzer disabled successfully');
      })
      .catch(err => {
        if (!_.isString(err)) {
          this.NotificationService.error('Unable to delete the analyzer.');
        }
      });
  }

  reload() {
    this.OrganizationService.analyzers(this.organization.id).then(analyzers => {
      this.analyzers = analyzers;
      this.$onInit();
    });
  }
}
