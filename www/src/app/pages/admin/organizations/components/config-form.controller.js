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

    if (!this.configuration[config.name]) {
      this.configuration[config.name] = [];
    }

    this.configuration[config.name].push(defaultValues[config.type]);
  }

  removeOption(config, index) {
    if (this.configuration[config].length === 1) {
      this.configuration[config] = [undefined];
    } else {
      this.configuration[config].splice(index, 1);
    }
  }
}
