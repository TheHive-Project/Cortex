'use strict';

import lo from 'lodash';

export default function(app) {
  function UserService($resource, $http, $q) {
    'ngInject';

    let res = $resource(
      './api/user/:userId',
      {},
      {
        query: {
          method: 'POST',
          url: './api/user/_search',
          isArray: true
        },
        update: {
          method: 'PATCH'
        },
        changePass: {
          method: 'POST',
          url: './api/user/:userId/password/change'
        },
        setPass: {
          method: 'POST',
          url: './api/user/:userId/password/set'
        },
        setKey: {
          method: 'POST',
          url: './api/user/:userId/key/renew'
        },
        revokeKey: {
          method: 'DELETE',
          url: './api/user/:userId/key'
        }
      }
    );

    res.getUserInfo = function(login) {
      let defer = $q.defer();

      if (login === 'init') {
        defer.resolve({
          name: 'System'
        });
      } else {
        res.get(
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
    };

    res.getKey = function(userId) {
      let defer = $q.defer();

      $http.get('./api/user/' + userId + '/key').then(response => {
        defer.resolve(response.data);
      });

      return defer.promise;
    };

    res.list = function(query) {
      let defer = $q.defer();

      let post = {
        range: 'all',
        query: query
      };

      $http.post('./api/user/_search', post).then(response => {
        defer.resolve(response.data);
      });

      return defer.promise;
    };

    res.autoComplete = function(query) {
      return res
        .list({
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
    };

    return res;
  }

  app.factory('UserService', UserService);
}
