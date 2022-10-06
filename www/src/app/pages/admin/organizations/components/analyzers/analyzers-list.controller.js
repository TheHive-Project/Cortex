'use strict';

import _ from 'lodash';

import AnalyzerEditController from './analyzer.edit.controller';
import editModalTpl from './analyzer.edit.modal.html';

export default class OrganizationAnalyzersController {
  constructor(
    $log,
    $q,
    $uibModal,
    AnalyzerService,
    OrganizationService,
    ModalService,
    NotificationService
  ) {
    'ngInject';

    this.$log = $log;
    this.$q = $q;
    this.$uibModal = $uibModal;
    this.AnalyzerService = AnalyzerService;
    this.OrganizationService = OrganizationService;
    this.ModalService = ModalService;
    this.NotificationService = NotificationService;

    this.state = {
      filterAvailable: ''
    };
  }

  $onInit() {
    this.activeAnalyzers = _.keyBy(this.analyzers, 'analyzerDefinitionId');
    this.definitionsIds = _.keys(this.analyzerDefinitions).sort();
    this.obsoleteAnalyzers = _.filter(this.analyzers, a => !this.definitionsIds.includes(a.workerDefinitionId));
  }

  openModal(mode, definition, analyzer) {
    let baseConfigName = definition ? definition.baseConfig : undefined;
    return this.AnalyzerService.getBaseConfig(baseConfigName)
      .then(baseConfig => {
        let configs = {
          globalConfig: {},
          baseConfig: baseConfig,
          analyzerConfig: {
            config: {}
          }
        };

        return this.AnalyzerService.getConfiguration('global').then(
          globalConfig => {
            configs.globalConfig = globalConfig;

            if (!baseConfig.config) {
              baseConfig.config = {};
            }

            _.merge(
              configs.analyzerConfig.config,
              configs.baseConfig.config,
              configs.globalConfig.config
            );

            return configs;
          }
        );
      })
      .then(configs => {
        let modal = this.$uibModal.open({
          animation: true,
          controller: AnalyzerEditController,
          controllerAs: '$modal',
          templateUrl: editModalTpl,
          size: 'lg',
          resolve: {
            definition: () => definition,
            globalConfig: () => configs.globalConfig,
            baseConfig: () => configs.baseConfig,
            configuration: () => configs.analyzerConfig,
            analyzer: () => angular.copy(analyzer),
            mode: () => mode
          }
        });

        return modal.result;
      })
      .then(response => {
        if (mode === 'create') {
          return this.OrganizationService.enableAnalyzer(
            definition.id,
            response
          );
        } else {
          return this.OrganizationService.updateAnalyzer(
            analyzer.id,
            _.pick(
              response,
              'configuration',
              'rate',
              'rateUnit',
              'name',
              'jobCache',
              'jobTimeout'
            )
          );
        }
      })
      .then(() => this.reload());
  }

  edit(mode, definition, analyzer) {
    this.openModal(mode, definition, analyzer)
      .then(() => {
        this.NotificationService.success('Analyzer updated successfully');
      })
      .catch(err => {
        if (!_.isString(err)) {
          this.NotificationService.error('Failed to edit the analyzer.');
        }
      });
  }

  enable(analyzerId) {
    let definition = this.analyzerDefinitions[analyzerId];

    this.openModal('create', definition, {})
      .then(() => {
        this.NotificationService.success('Analyzer enabled successfully');
      })
      .catch(err => {
        if (!_.isString(err)) {
          this.NotificationService.error('Failed to enable the analyzer.');
        }
      });
  }

  disable(analyzerId) {
    let modalInstance = this.ModalService.confirm(
      'Disable analyzer',
      'Are you sure you want to disable this analyzer? The corresponding configuration will be lost.', {
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

  refreshAnalyzers() {
    this.AnalyzerService.scan()
      .then(() => this.AnalyzerService.definitions(true))
      .then(defintions => {
        this.analyzerDefinitions = defintions;
        this.reload();
        this.NotificationService.success('Analyzer definitions refreshed.');
      })
      .catch(() =>
        this.NotificationService.error(
          'Failed to refresh analyzer definitions.'
        )
      );
  }
}