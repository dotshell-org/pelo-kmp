import ActivityKit
import Foundation

/// Shape of the navigation Live Activity, compiled into both the app and the widget extension:
/// the app starts and updates the activity, the extension renders it, and they must agree on the
/// type exactly.
///
/// `ContentState` is what changes during a journey; the attributes outside it are fixed for the
/// activity's whole life.
@available(iOS 16.1, *)
public struct PeloNavigationAttributes: ActivityAttributes {

    /// One stretch of the journey. `seconds` is a duration, not a position: the segments are
    /// consecutive and their sum is the whole trip, which is what puts progress on the same scale
    /// without the renderer having to be told the geometry twice.
    public struct Segment: Codable, Hashable {

        public enum Kind: String, Codable, Hashable {
            case walk
            /// Standing at a stop between two legs — as much a part of the journey as the rest.
            case wait
            case ride
        }

        public var seconds: Int
        public var kind: Kind
        /// The line's own colour, packed RGB as the shared code carries it. Nil unless a ride.
        public var colorArgb: Int?

        public init(seconds: Int, kind: Kind, colorArgb: Int?) {
            self.seconds = seconds
            self.kind = kind
            self.colorArgb = colorArgb
        }
    }

    public struct ContentState: Codable, Hashable {
        /// The instruction as shown in the app, already localised there.
        public var instruction: String
        /// Line badge text, absent while walking.
        public var lineName: String?
        public var remainingMinutes: Int
        /// Pre-formatted clock time — formatting it here would risk a different locale.
        public var arrivalTime: String
        public var isArrived: Bool
        public var segments: [Segment]
        /// Offsets into the journey, in seconds, where the traveller changes line.
        public var transferOffsetsSeconds: [Int]
        public var progressSeconds: Int
        /// Sum of `segments`. Zero for a journey with no measurable duration.
        public var totalSeconds: Int

        /// Where along the bar the traveller is, `0...1`. Zero when there is nothing to divide by.
        public var progressFraction: Double {
            guard totalSeconds > 0 else { return 0 }
            return min(1, max(0, Double(progressSeconds) / Double(totalSeconds)))
        }

        public init(
            instruction: String,
            lineName: String?,
            remainingMinutes: Int,
            arrivalTime: String,
            isArrived: Bool,
            segments: [Segment],
            transferOffsetsSeconds: [Int],
            progressSeconds: Int,
            totalSeconds: Int
        ) {
            self.instruction = instruction
            self.lineName = lineName
            self.remainingMinutes = remainingMinutes
            self.arrivalTime = arrivalTime
            self.isArrived = isArrived
            self.segments = segments
            self.transferOffsetsSeconds = transferOffsetsSeconds
            self.progressSeconds = progressSeconds
            self.totalSeconds = totalSeconds
        }
    }

    /// Where the journey ends, shown as the activity's fixed subtitle. Outside the content state
    /// because a reroute keeps the destination — only the way there changes.
    public var destination: String

    public init(destination: String) {
        self.destination = destination
    }
}
