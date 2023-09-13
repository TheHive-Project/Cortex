'use strict';

export default function (app) {
  app
    .constant('UrlParser', window.url)
    .constant('ROUTE_ERRORS', {
      auth: 'Authorization has been denied.'
    })
    .constant('Roles', {
      SUPERADMIN: 'superadmin',
      ORGADMIN: 'orgadmin',
      ANALYZE: 'analyze',
      READ: 'read'
    })
    .value('Tlps', [{
      key: 'CLEAR',
      value: 0
    },
    {
      key: 'GREEN',
      value: 1
    },
    {
      key: 'AMBER',
      value: 2
    },
    {
      key: 'AMBER-STRICT',
      value: 3
    },
    {
      key: 'RED',
      value: 4
    }
    ]);
}