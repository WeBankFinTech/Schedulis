package azkaban.project.validator;

/**
 * Status of the ValidationReport. It also represents the severity of each rule. The order of
 * severity for the status is PASS < WARN < ERROR.
 * @author WTSS
 */
public enum ValidationStatus {
  PASS("PASS"),
  WARN("WARN"),
  ERROR("ERROR");

  private final String status;

  private ValidationStatus(final String status) {
    this.status = status;
  }

  @Override
  public String toString() {
    return this.status;
  }
}
