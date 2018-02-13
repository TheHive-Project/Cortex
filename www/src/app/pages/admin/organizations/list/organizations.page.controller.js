'use strict';

import _ from 'lodash/core';

import OrganizationModalController from '../components/organization.modal.controller';
import organizationModalTpl from '../components/organization.modal.html';

export default class OrganizationsPageController {
  constructor(
    $log,
    $uibModal,
    OrganizationService,
    NotificationService,
    ModalService
  ) {
    'ngInject';

    this.$log = $log;
    this.$uibModal = $uibModal;
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

  openModal(mode, organization) {
    let modal = this.$uibModal.open({
      controller: OrganizationModalController,
      controllerAs: '$modal',
      templateUrl: organizationModalTpl,
      size: 'lg',
      resolve: {
        organization: () => organization,
        mode: () => mode
      }
    });

    modal.result
      .then(org => {
        if (mode === 'edit') {
          this.update(org.id, org);
        } else {
          this.create(org);
        }
      })
      .catch(err => {
        if (!_.isString(err)) {
          this.NotificationService.error('Unable to save the organization.');
        }
      });
  }

  create(organization) {
    this.OrganizationService.create(organization).then(() => {
      this.NotificationService.error('Organization created successfully');
      this.reload();
      this.$onInit();
    });
  }
  update(id, organization) {
    this.OrganizationService.update(organization).then(() => {
      this.NotificationService.error('Organization updated successfully');
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
