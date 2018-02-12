'use strict';

import lo from 'lodash';

export default class UserService {
  constructor($resource, $http, $q) {
    'ngInject';

    this.$q = $q;
    this.$http = $http;
  }

  query(config) {
    let defer = this.$q.defer();

    this.$http.post(`./api/user/_search`, config).then(response => {
      defer.resolve(response.data);
    });

    return defer.promise;
  }

  save(user) {
    return this.$http
      .post('./api/user', user)
      .then(response => this.$q.resolve(response.data));
  }

  update(id, user) {
    let defer = this.$q.defer();

    this.$http.patch(`./api/user/${id}`, user).then(response => {
      defer.resolve(response.data);
    });

    return defer.promise;
  }

  changePass(id, password) {
    return this.$http
      .post(`./api/user/${id}/password/change`, {
        password: password
      })
      .then(response => this.$q.resolve(response.data));
  }

  setPass(id, password) {
    return this.$http
      .post(`./api/user/${id}/password/set`, {
        password: password
      })
      .then(response => this.$q.resolve(response.data));
  }

  setKey(id) {
    return this.$http
      .post(`./api/user/${id}/key/renew`)
      .then(response => this.$q.resolve(response.data));
  }

  revokeKey(id) {
    return this.$http
      .delete(`./api/user/${id}/key`)
      .then(response => this.$q.resolve(response.data));
  }

  getUserInfo(login) {
    let defer = this.$q.defer();

    if (login === 'init') {
      defer.resolve({
        name: 'System'
      });
    } else {
      this.get(
        {
          userId: login
        },
        user => {
          defer.resolve(user);
        },
        (data, status) => {
          data.name = '***unknown***';
          defer.reject(data, status);
        }
      );
    }

    return defer.promise;
  }

  getKey(userId) {
    return this.$http
      .get('./api/user/' + userId + '/key')
      .then(response => this.$q.resolve(response.data));
  }

  list(query) {
    let post = {
      range: 'all',
      query: query
    };

    return this.$http
      .post('./api/user/_search', post)
      .then(response => this.$q.resolve(response.data));
  }

  autoComplete(query) {
    return this.list({
      _and: [
        {
          status: 'Ok'
        }
      ]
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
}
