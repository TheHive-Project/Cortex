'use strict';

export default class HtmlSanitizer {
  constructor($sanitize) {
    'ngInject';

    this.$sanitize = $sanitize;
    this.entityMap = {
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;',
      '/': '&#x2F;'
    };
  }

  sanitize(str) {
    return this.$sanitize(
      String(str).replace(/[&<>"'\/]/g, s => this.entityMap[s])
    );
  }
}
