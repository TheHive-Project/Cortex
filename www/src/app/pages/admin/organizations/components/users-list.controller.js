'use strict';

import UserEditController from './user.edit.controller';
import editModalTpl from './user.edit.modal.html';

export default class OrganizationUsersListController {
  constructor(
    $log,
    $uibModal,
    OrganizationService,
    UserService,
    NotificationService,
    clipboard
  ) {
    'ngInject';

    this.$log = $log;
    this.$uibModal = $uibModal;
    this.OrganizationService = OrganizationService;
    this.UserService = UserService;
    this.NotificationService = NotificationService;
    this.clipboard = clipboard;

    this.userKeyCache = {};
  }

  $onInit() {
    this.canSetPass =
      this.main.config.config.capabilities.indexOf('setPassword') !== -1;
  }

  reload() {
    this.OrganizationService.users(this.organization.id).then(users => {
      this.$log.log(users);
      this.users = users;
    });
  }

  getKey(user) {
    this.UserService.getKey(user.id).then(key => {
      this.userKeyCache[user.id] = key;
    });
  }

  createKey(user) {
    this.UserService.setKey(user.id)
      .then(() => {
        delete this.userKeyCache[user.id];
        this.reload();
      })
      .catch(response => {
        this.NotificationService.error(
          'AdminUsersCtrl',
          response.data,
          response.status
        );
      });
  }

  revokeKey(user) {
    this.UserService.revokeKey(user.id)
      .then(() => {
        delete this.userKeyCache[user.id];
        this.reload();
      })
      .catch(response => {
        this.NotificationService.error(
          'OrganizationUsersListController',
          response.data,
          response.status
        );
      });
  }

  copyKey(user) {
    this.clipboard.copyText(this.userKeyCache[user.id]);
    delete this.userKeyCache[user.id];
  }

  setPassword(user, password) {
    if (!this.canSetPass) {
      return;
    }

    this.UserService.setPass(user.id, password)
      .then(() => {
        this.NotificationService.log(
          'The password of user [' +
            user.id +
            '] has been successfully updated',
          'success'
        );
      })
      .catch(response => {
        this.NotificationService.error(
          'OrganizationUsersListController',
          response.data,
          response.status
        );
      });
  }

  openModal(mode, user) {
    let modal = this.$uibModal.open({
      animation: true,
      controller: UserEditController,
      controllerAs: '$modal',
      templateUrl: editModalTpl,
      size: 'lg',
      resolve: {
        organization: () => this.organization,
        user: () => angular.copy(user),
        mode: () => mode
      }
    });

    modal.result.then(/*response*/ () => this.reload()).catch(rejection => {
      if (rejection && rejection.type === 'ConflictError') {
        // Handle user uniquness
      }
      this.$log.log(rejection);
    });
  }
}
