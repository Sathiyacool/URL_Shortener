Feature: URL shortener behavior
  The public API creates links, redirects visitors, and records analytics while respecting expiration.

  Scenario: Create a short link and record redirect analytics
    Given the shortener has no stored links
    When a user shortens "https://example.com/articles" using alias "bdd1"
    Then the response status is 201
    When a visitor follows the returned link from "https://referrer.example"
    Then the redirect points to "https://example.com/articles"
    And analytics reports 1 click

  Scenario: Expired links do not redirect or record clicks
    Given expired link "gone1" exists
    When a visitor follows expired link "gone1"
    Then the response status is 410
    And expired link "gone1" has no recorded clicks

  Scenario: Reject non-HTTP destinations
    When a user submits "file:///etc/passwd" as a destination
    Then the response status is 400
