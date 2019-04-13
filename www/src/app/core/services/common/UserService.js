'use strict';

import lo from 'lodash';

import UserEditController from '../../../components/user-dialog/user.edit.controller';
import editModalTpl from '../../../components/user-dialog/user.edit.modal.html';

export default class UserService {
  constructor($resource, $http, $q, $uibModal) {
    'ngInject';

    this.$q = $q;
    this.$http = $http;
    this.$uibModal = $uibModal;

    this.userCache = {};
  }

  query(config) {
    return this.$http.post(`./api/user/_search`, config)
      .then(response => this.$q.resolve(response.data))
      .catch(err => this.$q.reject(err));
  }

  get(user) {
    return this.$http
      .get(`./api/user/${user}`)
      .then(response => this.$q.resolve(response.data))
      .catch(err => this.$q.reject(err));
  }

  save(user) {
    return this.$http
      .post('./api/user', user)
      .then(response => this.$q.resolve(response.data))
      .catch(err => this.$q.reject(err));
  }

  update(id, user) {
    let defer = this.$q.defer();

    this.$http
      .patch(`./api/user/${id}`, user)
      .then(response => {
        defer.resolve(response.data);
      })
      .catch(err => defer.reject(err));

    return defer.promise;
  }

  changePass(id, currentPassword, password) {
    return this.$http
      .post(`./api/user/${id}/password/change`, {
        currentPassword: currentPassword,
        password: password
      })
      .then(response => this.$q.resolve(response.data))
      .catch(err => this.$q.reject(err));
  }

  setPass(id, password) {
    return this.$http
      .post(`./api/user/${id}/password/set`, {
        password: password
      })
      .then(response => this.$q.resolve(response.data))
      .catch(err => this.$q.reject(err));
  }

  setKey(id) {
    return this.$http
      .post(`./api/user/${id}/key/renew`)
      .then(response => this.$q.resolve(response.data))
      .catch(err => this.$q.reject(err));
  }

  revokeKey(id) {
    return this.$http
      .delete(`./api/user/${id}/key`)
      .then(response => this.$q.resolve(response.data))
      .catch(err => this.$q.reject(err));
  }

  getUserInfo(login) {
    let defer = this.$q.defer();

    if (login === 'init') {
      defer.resolve({
        name: 'System'
      });
    } else {
      this.get(login)
        .then(user => {
          defer.resolve(user);
        })
        .catch(err => {
          err.data.name = '***unknown***';
          defer.reject(err);
        });
    }

    return defer.promise;
  }

  getKey(userId) {
    return this.$http
      .get('./api/user/' + userId + '/key')
      .then(response => this.$q.resolve(response.data))
      .catch(err => this.$q.reject(err));
  }

  list(query) {
    let post = {
      range: 'all',
      query: query
    };

    return this.$http
      .post('./api/user/_search', post)
      .then(response => this.$q.resolve(response.data))
      .catch(err => this.$q.reject(err));
  }

  autoComplete(query) {
    return this.list({
        _and: [{
          status: 'Ok'
        }]
      })
      .then(data =>
        lo.map(data, user => ({
          label: user.name,
          text: user.id
        }))
      )
      .then(users =>
        lo.filter(users, user => {
          let regex = new RegExp(query, 'gi');

          return regex.test(user.label);
        })
      );
  }

  getCache(userId) {
    if (angular.isDefined(this.userCache[userId])) {
      return this.$q.resolve(this.userCache[userId]);
    } else {
      let defer = this.$q.defer();

      this.getUserInfo(userId)
        .then(userInfo => {
          this.userCache[userId] = userInfo;
          defer.resolve(userInfo);
        })
        .catch(() => defer.resolve(undefined));

      return defer.promise;
    }
  }

  clearCache() {
    this.userCache = {};
  }

  removeCache(userId) {
    delete this.userCache[userId];
  }

  updateCache(userId, userData) {
    this.userCache[userId] = userData;
  }

  openModal(org, mode, user) {
    let modal = this.$uibModal.open({
      animation: true,
      controller: UserEditController,
      controllerAs: '$modal',
      templateUrl: editModalTpl,
      size: 'lg',
      resolve: {
        organization: () => org,
        user: () => angular.copy(user),
        mode: () => mode
      }
    });

    return modal.result;
  }
}