'use strict';

import _ from 'lodash/core';

import PageController from '../../../../core/controllers/PageController';

import OrganizationModalController from '../components/organization.modal.controller';
import organizationModalTpl from '../components/organization.modal.html';

export default class OrganizationsPageController extends PageController {
  constructor(
    $log,
    $uibModal,
    SearchService,
    OrganizationService,
    NotificationService,
    ModalService,
    localStorageService
  ) {
    'ngInject';

    super('organizations-page');

    this.$log = $log;
    this.$uibModal = $uibModal;
    this.OrganizationService = OrganizationService;
    this.NotificationService = NotificationService;
    this.ModalService = ModalService;
    this.SearchService = SearchService;
    this.localStorageService = localStorageService;

    this.pagination = {
      current: 1,
      total: 0
    };

    this.state = this.localStorageService.get('organizations-page') || {
      filters: {
        search: null,
        status: []
      },
      pagination: {
        pageSize: 50,
        current: 1
      }
    };

    this.filters = this.state.filters;
    this.pagination = this.state.pagination;
    this.statuses = ['Active', 'Locked'];
  }

  $onInit() {
    this.load(1);
  }

  buildQuery() {
    let criteria = [];

    if (!_.isEmpty(this.filters.search)) {
      criteria.push({
        _like: {
          _field: 'description',
          _value: this.filters.search
        }
      });
    }

    if (!_.isEmpty(this.filters.status)) {
      criteria.push({
        _in: {
          _field: 'status',
          _values: this.filters.status
        }
      });
    }

    return _.isEmpty(criteria)
      ? {}
      : criteria.length === 1
        ? criteria[0]
        : {
            _and: criteria
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
      this.NotificationService.success('Organization created successfully');
      this.load();
      this.$onInit();
    });
  }
  update(id, organization) {
    this.OrganizationService.update(id, organization).then(() => {
      this.NotificationService.success('Organization updated successfully');
      this.load();
      this.$onInit();
    });
  }

  disable(id) {
    if (id === 'cortex') {
      return;
    }

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
        this.load(1);
        this.NotificationService.success('The organization has been disabled');
      })
      .catch(err => {
        if (!_.isString(err)) {
          this.NotificationService.error('Unable to disable the organization.');
        }
      });
  }

  enable(id) {
    if (id === 'cortex') {
      return;
    }

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
        this.load(1);
        this.NotificationService.success('The organization has been enabled');
      })
      .catch(err => {
        if (!_.isString(err)) {
          this.NotificationService.error('Unable to enabled the organization.');
        }
      });
  }

  load(page) {
    if (page) {
      this.pagination.current = page;
    }

    this.state.filters = this.filters;
    this.state.pagination = {
      pageSize: this.pagination.pageSize
    };
    this.localStorageService.set('organizations-page', this.state);

    this.SearchService.configure({
      objectType: 'organization',
      filter: this.buildQuery(),
      range: this.buildRange()
    })
      .search()
      .then(response => {
        this.organizations = response.data;
        this.pagination.total = parseInt(response.headers('x-total')) || 0;
      });
  }
}
