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
        key: 'WHITE',
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
        key: 'RED',
        value: 3
      }
    ]);
}