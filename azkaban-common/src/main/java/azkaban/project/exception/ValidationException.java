package azkaban.project.exception;

import azkaban.project.validator.ValidationReport;

/**
 * @author lebronwang
 * @date 2025/07/25
 **/
public class ValidationException extends Exception {

  private final ValidationReport report;

  public ValidationException(String message, ValidationReport report) {
    super(message);
    this.report = report;
  }

  public ValidationReport getReport() {
    return report;
  }
}

