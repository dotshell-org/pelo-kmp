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

    public struct ContentState: Codable, Hashable {
        /// The instruction as shown in the app, already localised there.
        public var instruction: String
        /// Line badge text, absent while walking.
        public var lineName: String?
        public var remainingMinutes: Int
        /// Pre-formatted clock time — formatting it here would risk a different locale.
        public var arrivalTime: String
        public var isArrived: Bool

        public init(
            instruction: String,
            lineName: String?,
            remainingMinutes: Int,
            arrivalTime: String,
            isArrived: Bool
        ) {
            self.instruction = instruction
            self.lineName = lineName
            self.remainingMinutes = remainingMinutes
            self.arrivalTime = arrivalTime
            self.isArrived = isArrived
        }
    }

    /// Where the journey ends, shown as the activity's fixed subtitle.
    public var destination: String

    public init(destination: String) {
        self.destination = destination
    }
}
