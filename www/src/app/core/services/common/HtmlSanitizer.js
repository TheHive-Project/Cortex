'use strict';

export default function(app) {
  app.service('HtmlSanitizer', function($sanitize) {
    'ngInject';

    let entityMap = {
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;',
      '/': '&#x2F;'
    };

    this.sanitize = function(str) {
      return $sanitize(
        String(str).replace(/[&<>"'\/]/g, function(s) {
          return entityMap[s];
        })
      );
    };
  });
}
