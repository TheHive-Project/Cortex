'use strict';

import _ from 'lodash';

import ResponderEditController from './responder.edit.controller';
import editModalTpl from './responder.edit.modal.html';

export default class OrganizationRespondersController {
  constructor(
    $log,
    $q,
    $uibModal,
    ResponderService,
    OrganizationService,
    ModalService,
    NotificationService
  ) {
    'ngInject';

    this.$log = $log;
    this.$q = $q;
    this.$uibModal = $uibModal;
    this.ResponderService = ResponderService;
    this.OrganizationService = OrganizationService;
    this.ModalService = ModalService;
    this.NotificationService = NotificationService;

    this.state = {
      filterAvailable: ''
    };
  }

  $onInit() {
    this.activeResponders = _.keyBy(this.responders, 'workerDefinitionId');
    this.definitionsIds = _.keys(this.responderDefinitions).sort();
    this.invalidResponders = _.filter(this.responders, a =>
      _.isEmpty(a.dataTypeList)
    );
  }

  openModal(mode, definition, responder) {
    let baseConfigName = definition ? definition.baseConfig : undefined;
    return this.ResponderService.getBaseConfig(baseConfigName)
      .then(baseConfig => {
        let configs = {
          globalConfig: {},
          baseConfig: baseConfig,
          responderConfig: {
            config: {}
          }
        };

        return this.ResponderService.getConfiguration('global').then(
          globalConfig => {
            configs.globalConfig = globalConfig;

            if (!baseConfig.config) {
              baseConfig.config = {};
            }

            _.merge(
              configs.responderConfig.config,
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
          controller: ResponderEditController,
          controllerAs: '$modal',
          templateUrl: editModalTpl,
          size: 'lg',
          resolve: {
            definition: () => definition,
            globalConfig: () => configs.globalConfig,
            baseConfig: () => configs.baseConfig,
            configuration: () => configs.responderConfig,
            responder: () => angular.copy(responder),
            mode: () => mode
          }
        });

        return modal.result;
      })
      .then(response => {
        if (mode === 'create') {
          return this.OrganizationService.enableResponder(
            definition.id,
            response
          );
        } else {
          return this.OrganizationService.updateResponder(
            responder.id,
            _.pick(
              response,
              'configuration',
              'rate',
              'rateUnit',
              'name',
              'jobTimeout'
            )
          );
        }
      })
      .then(() => this.reload());
  }

  edit(mode, definition, responder) {
    this.openModal(mode, definition, responder)
      .then(() => {
        this.NotificationService.success('Responder updated successfully');
      })
      .catch(err => {
        if (!_.isString(err)) {
          this.NotificationService.error('Failed to edit the responder.');
        }
      });
  }

  enable(responderId) {
    let definition = this.responderDefinitions[responderId];

    this.openModal('create', definition, {})
      .then(() => {
        this.NotificationService.success('Responder enabled successfully');
      })
      .catch(err => {
        if (!_.isString(err)) {
          this.NotificationService.error('Failed to enable the responder.');
        }
      });
  }

  disable(responderId) {
    let modalInstance = this.ModalService.confirm(
      'Disable responder',
      'Are you sure you want to disable this responder? The corresponding configuration will be lost.', {
        flavor: 'danger',
        okText: 'Yes, disable it'
      }
    );

    modalInstance.result
      .then(() => this.OrganizationService.disableResponder(responderId))
      .then(() => {
        this.reload();
        this.NotificationService.success('Responder disabled successfully');
      })
      .catch(err => {
        if (!_.isString(err)) {
          this.NotificationService.error('Unable to delete the responder.');
        }
      });
  }

  reload() {
    this.OrganizationService.responders(this.organization.id).then(responders => {
      this.responders = responders;
      this.$onInit();
    });
  }

  refreshResponders() {
    this.ResponderService.scan()
      .then(() => this.ResponderService.definitions(true))
      .then(defintions => {
        this.responderDefinitions = defintions;
        this.reload();
        this.NotificationService.success('Responder definitions refreshed.');
      })
      .catch(() =>
        this.NotificationService.error(
          'Failed to refresh responder definitions.'
        )
      );
  }
}