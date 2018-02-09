'use strict';

import _ from 'lodash/core';

export default class OrganizationsPageController {
  constructor($log, OrganizationService, NotificationService, ModalService) {
    'ngInject';

    this.$log = $log;
    this.OrganizationService = OrganizationService;
    this.NotificationService = NotificationService;
    this.ModalService = ModalService;
  }

  $onInit() {
    this.state = {
      showForm: false,
      formData: {}
    };
  }

  create() {
    this.OrganizationService.create(
      _.pick(this.state.formData, ['name', 'description'])
    ).then(response => {
      this.$log.log('Organization created', response);
      this.reload();
      this.$onInit();
    });
  }

  disable(id) {
    let modalInstance = this.ModalService.confirm(
      'Disable organization',
      `Are your sure you want to disable this organization? Its users will no longer be able to have access to Cortex.`,
      {
        flavor: 'danger',
        okText: 'Yes, disable it'
      }
    );

    modalInstance.result
      .then(() => this.OrganizationService.disable(id))
      .then(() => {
        this.reload();
        this.NotificationService.success('The organization has been disabled');
      })
      .catch(err => {
        if (!_.isString(err)) {
          this.NotificationService.error('Unable to disable the organization.');
        }
      });
  }

  enable(id) {
    let modalInstance = this.ModalService.confirm(
      'Enable organization',
      `Are your sure you want to enable this organization? Its users will have access to Cortex.`,
      {
        flavor: 'primary',
        okText: 'Yes, enable it'
      }
    );

    modalInstance.result
      .then(() => this.OrganizationService.enable(id))
      .then(() => {
        this.reload();
        this.NotificationService.success('The organization has been enabled');
      })
      .catch(err => {
        if (!_.isString(err)) {
          this.NotificationService.error('Unable to enabled the organization.');
        }
      });
  }

  reload() {
    this.OrganizationService.list().then(
      organizations => (this.organizations = organizations)
    );
  }
}
