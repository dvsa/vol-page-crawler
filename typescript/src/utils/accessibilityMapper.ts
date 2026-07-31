/** Port of org.dvsa.testing.framework.utils.AccessibilityMapper. */
export class AccessibilityMapper {
  static mapAxeRuleToGovPaths(axeRuleId: string): string[] {
    switch (axeRuleId) {
      case 'color-contrast':
        return ['colour', 'button'];
      case 'region':
      case 'main-role':
        return ['layout'];
      case 'button-name':
        return ['button'];
      default:
        return ['headings'];
    }
  }
}
