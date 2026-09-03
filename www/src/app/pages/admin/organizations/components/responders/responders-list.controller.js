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

    // Predicate used by the template to filter rows on the definition id or the responder name
    this.rowFilter = row => {
      const needle = (this.state.filterAvailable || '').toLowerCase();

      if (!needle) {
        return true;
      }

      return (
        row.definition.id.toLowerCase().includes(needle) ||
        (row.responder && (row.responder.name || '').toLowerCase().includes(needle))
      );
    };
  }

  $onInit() {
    // A definition can be enabled several times (duplicates): group the enabled responders by definition
    this.activeResponders = _.groupBy(this.responders, 'workerDefinitionId');
    this.definitionsIds = _.keys(this.responderDefinitions).sort();
    this.obsoleteResponders = _.filter(this.responders, a => !this.definitionsIds.includes(a.workerDefinitionId));

    // One row per enabled responder, or a single row for a definition that is not enabled yet
    this.rows = _.flatMap(this.definitionsIds, def => {
      const definition = this.responderDefinitions[def];
      const instances = _.sortBy(this.activeResponders[def] || [], 'name');

      if (instances.length === 0) {
        return [{ definition: definition, responder: null }];
      }

      return _.map(instances, responder => ({ definition: definition, responder: responder }));
    });
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
        } else if (mode === 'duplicate') {
          return this.OrganizationService.duplicateResponder(
            responder.id,
            _.pick(
              response,
              'name',
              'description',
              'configuration',
              'rate',
              'rateUnit',
              'jobTimeout'
            )
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
          this.NotificationService.error(
            'Failed to edit the responder.' + this.serverMessage(err)
          );
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
          this.NotificationService.error(
            'Failed to enable the responder.' + this.serverMessage(err)
          );
        }
      });
  }

  // Message returned by the Cortex API, if any, to append to a notification
  serverMessage(err) {
    return err && err.data && err.data.message ? ` ${err.data.message}` : '';
  }

  duplicate(definition, responder) {
    // Pre-fill the copy with the source values; the name must be changed by the user
    const copy = _.assign({}, responder, {
      name: `${responder.name} (copy)`
    });

    this.openModal('duplicate', definition, copy)
      .then(() => {
        this.NotificationService.success('Responder duplicated successfully');
      })
      .catch(err => {
        if (!_.isString(err)) {
          this.NotificationService.error(
            'Failed to duplicate the responder.' + this.serverMessage(err)
          );
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