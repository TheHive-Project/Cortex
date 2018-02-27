'use strict';

export default class ConfigurationForm {
  constructor() {}

  typeOf(config) {
    return `${config.multi ? 'multi-' : ''}${config.type}`;
  }

  addOption(config) {
    let defaultValues = {
      string: null,
      number: 0,
      boolean: true
    };
    this.items[config.name].push(defaultValues[config.type]);
  }
}
