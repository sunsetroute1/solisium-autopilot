use serde::Serialize;

#[derive(Debug, Clone, Copy, Serialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum ReferenceKind {
    NeutralInternet,
    AwsRegionalReference,
}

#[derive(Debug, Clone)]
pub struct ReferenceTarget {
    pub id: String,
    pub label: String,
    pub kind: ReferenceKind,
    pub hostname: String,
    pub aws_region_id: Option<String>,
    pub geo_hint: String,
}

/// Public hostnames only — IPs resolved at runtime via DNS (not T&L servers).
pub fn all_references() -> Vec<ReferenceTarget> {
    vec![
        ReferenceTarget {
            id: "neutral_cloudflare".into(),
            label: "Cloudflare DNS (neutral)".into(),
            kind: ReferenceKind::NeutralInternet,
            hostname: "1.1.1.1".into(),
            aws_region_id: None,
            geo_hint: "Anycast DNS — not AWS".into(),
        },
        ReferenceTarget {
            id: "neutral_google".into(),
            label: "Google DNS (neutral)".into(),
            kind: ReferenceKind::NeutralInternet,
            hostname: "8.8.8.8".into(),
            aws_region_id: None,
            geo_hint: "Anycast DNS — not AWS".into(),
        },
        ReferenceTarget {
            id: "neutral_quad9".into(),
            label: "Quad9 DNS (neutral)".into(),
            kind: ReferenceKind::NeutralInternet,
            hostname: "9.9.9.9".into(),
            aws_region_id: None,
            geo_hint: "Anycast DNS — not AWS".into(),
        },
        ReferenceTarget {
            id: "aws_us_east_1".into(),
            label: "AWS EC2 us-east-1 (N. Virginia)".into(),
            kind: ReferenceKind::AwsRegionalReference,
            hostname: "ec2.us-east-1.amazonaws.com".into(),
            aws_region_id: Some("us-east-1".into()),
            geo_hint: "Northern Virginia".into(),
        },
        ReferenceTarget {
            id: "aws_us_east_2".into(),
            label: "AWS EC2 us-east-2 (Ohio)".into(),
            kind: ReferenceKind::AwsRegionalReference,
            hostname: "ec2.us-east-2.amazonaws.com".into(),
            aws_region_id: Some("us-east-2".into()),
            geo_hint: "Ohio / US Midwest edge".into(),
        },
        ReferenceTarget {
            id: "aws_us_west_1".into(),
            label: "AWS EC2 us-west-1 (N. California)".into(),
            kind: ReferenceKind::AwsRegionalReference,
            hostname: "ec2.us-west-1.amazonaws.com".into(),
            aws_region_id: Some("us-west-1".into()),
            geo_hint: "Northern California".into(),
        },
        ReferenceTarget {
            id: "aws_us_west_2".into(),
            label: "AWS EC2 us-west-2 (Oregon)".into(),
            kind: ReferenceKind::AwsRegionalReference,
            hostname: "ec2.us-west-2.amazonaws.com".into(),
            aws_region_id: Some("us-west-2".into()),
            geo_hint: "Oregon / US Pacific Northwest".into(),
        },
        ReferenceTarget {
            id: "aws_us_west_2_s3".into(),
            label: "AWS S3 us-west-2 (Oregon)".into(),
            kind: ReferenceKind::AwsRegionalReference,
            hostname: "s3.us-west-2.amazonaws.com".into(),
            aws_region_id: Some("us-west-2".into()),
            geo_hint: "Oregon (S3 regional endpoint)".into(),
        },
        ReferenceTarget {
            id: "aws_us_east_1_ddb".into(),
            label: "AWS DynamoDB us-east-1".into(),
            kind: ReferenceKind::AwsRegionalReference,
            hostname: "dynamodb.us-east-1.amazonaws.com".into(),
            aws_region_id: Some("us-east-1".into()),
            geo_hint: "Northern Virginia (DynamoDB)".into(),
        },
    ]
}
