-- phpMyAdmin SQL Dump
-- version 5.2.0
-- https://www.phpmyadmin.net/
--
-- Host: 127.0.0.1:3306
-- Generation Time: May 03, 2023 at 07:44 PM
-- Server version: 8.0.31
-- PHP Version: 8.0.26

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Database: `chatdb`
--

-- --------------------------------------------------------

--
-- Table structure for table `friends`
--

DROP TABLE IF EXISTS `friends`;
CREATE TABLE IF NOT EXISTS `friends` (
  `user1` int NOT NULL,
  `user2` int NOT NULL,
  PRIMARY KEY (`user1`,`user2`),
  KEY `FK_user2_userID` (`user2`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Dumping data for table `friends`
--

INSERT INTO `friends` (`user1`, `user2`) VALUES
(37, 36),
(35, 37),
(42, 37),
(45, 37);

-- --------------------------------------------------------

--
-- Table structure for table `messages`
--

DROP TABLE IF EXISTS `messages`;
CREATE TABLE IF NOT EXISTS `messages` (
  `messageID` int NOT NULL AUTO_INCREMENT,
  `sender` int NOT NULL,
  `receiver` int NOT NULL,
  `content` text NOT NULL,
  `date` datetime NOT NULL,
  PRIMARY KEY (`messageID`),
  KEY `FK_reciever_userID` (`receiver`),
  KEY `FK_sender_userID` (`sender`)
) ENGINE=InnoDB AUTO_INCREMENT=46 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Dumping data for table `messages`
--

INSERT INTO `messages` (`messageID`, `sender`, `receiver`, `content`, `date`) VALUES
(1, 46, 44, 'hello t', '2023-04-10 20:05:25'),
(2, 46, 42, 'hello t', '2023-04-10 21:15:25'),
(3, 36, 44, 'hello t', '2023-04-10 21:05:25'),
(4, 35, 44, 'hello t', '2023-04-10 13:05:25'),
(5, 35, 36, 'hello t', '2023-04-10 20:05:25'),
(6, 36, 46, 'hello t', '2023-04-10 22:05:25'),
(7, 42, 37, 'hello t', '2023-04-10 21:05:25'),
(8, 46, 42, 'hello t', '2023-04-10 17:05:25'),
(9, 46, 42, 'test --> new message send', '2023-04-11 21:36:25'),
(10, 46, 42, 'test --> new message send', '2023-04-11 21:36:25'),
(11, 46, 42, 'test --> new message send', '2023-04-11 21:36:25'),
(12, 46, 42, 'test --> new message send 12', '2023-04-11 21:36:25'),
(13, 37, 39, 'test --> new message sent to 39', '2023-04-11 21:36:25'),
(14, 37, 42, 'test --> new message sent to 39', '2023-04-11 21:36:25'),
(15, 37, 42, 'test --> new message sent to 42', '2023-04-11 21:36:25'),
(16, 37, 42, 'test --> new message sent to 42', '2023-04-11 21:36:25'),
(17, 37, 42, 'test --> new message sent to 42', '2023-04-11 21:37:25'),
(18, 37, 42, 'test --> new message sent to 42T9', '2023-04-09 21:37:25'),
(19, 37, 42, 'test --> new message sent to 42T9', '2023-04-11 21:37:25'),
(20, 37, 42, 'test --> new message sent to 42T9', '2023-04-11 21:36:26'),
(21, 37, 42, 'test --> new message sent to 42T10', '2023-04-12 21:36:26'),
(22, 37, 42, 'test --> new message sent to 42T10000000000000000000', '2023-04-12 21:36:26'),
(23, 37, 42, 'test --> new message sent to 42T100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000\n', '2023-04-12 21:36:26'),
(24, 37, 42, 'test --> new message test 1', '2023-04-12 21:36:26'),
(25, 37, 42, 'test --> new message test 2', '2023-04-12 21:36:26'),
(26, 37, 45, 'test --> new message test', '2023-04-12 21:36:26'),
(28, 37, 45, 'Hi', '0000-00-00 00:00:00'),
(29, 37, 45, 'Hi', '0000-00-00 00:00:00'),
(30, 37, 45, 'Hi', '0000-00-00 00:00:00'),
(31, 37, 45, 'Jju', '0000-00-00 00:00:00'),
(32, 37, 45, 'Hrllo', '0000-00-00 00:00:00'),
(33, 37, 45, 'test1', '2023-04-12 21:36:26'),
(34, 37, 45, 'test2', '0000-00-00 00:00:00'),
(35, 37, 45, 'test2', '0000-00-00 00:00:00'),
(36, 37, 45, 'test2', '0000-00-00 00:00:00'),
(37, 37, 45, 'test2', '2023-04-14 12:44:09'),
(38, 37, 45, 'Test3', '2023-04-14 12:50:09'),
(39, 37, 36, 'Hi', '2023-04-14 14:23:25'),
(40, 39, 37, 'hello', '2023-05-03 13:04:01'),
(41, 39, 37, 'hi', '2023-05-03 13:04:22'),
(42, 39, 37, 'yo', '2023-05-03 13:04:51'),
(43, 39, 37, 'hi', '2023-05-03 13:05:29'),
(44, 39, 37, 'hello 1', '2023-05-03 13:12:59'),
(45, 37, 39, 'Hd', '2023-05-03 13:13:04');

-- --------------------------------------------------------

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
CREATE TABLE IF NOT EXISTS `users` (
  `userID` int NOT NULL AUTO_INCREMENT,
  `userName` varchar(20) NOT NULL,
  `userEmail` varchar(50) NOT NULL,
  `userPassword` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  PRIMARY KEY (`userID`),
  UNIQUE KEY `userEmail` (`userEmail`)
) ENGINE=InnoDB AUTO_INCREMENT=50 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Dumping data for table `users`
--

INSERT INTO `users` (`userID`, `userName`, `userEmail`, `userPassword`) VALUES
(34, 'lyesrabhi16', 'lyesrabhi16@gmail.com0', '$argon2id$v=19$m=65536,t=3,p=4$ATMATm4R8GmS+Dumo1VWVA$RTj7R/Woh26Ij8DuSvpKT4XGnPatCo8DHU/WC57AU1M'),
(35, 'lyesrabhi16', 'lyesrabhi16@gmail.com1', '$argon2id$v=19$m=65536,t=3,p=4$SBP+zOj1gvDtYGMnepQrwg$zaqZluhE4Djr2Rpnt1aZDp0n5iXaqKqi5Unbt+bYrss'),
(36, 'lyesrabhi16', 'lyesrabhi16@gmail.com2', '$argon2id$v=19$m=65536,t=3,p=4$aS1DDUeJfSOCExfBxzNyrw$Vgt4RtNeChDP8PgubICHa+SAJnXU3q+v04JtiTjvs8Y'),
(37, 'lyesrabhi16', 'lyesrabhi16@gmail.com3', '$argon2id$v=19$m=65536,t=3,p=4$5GYVNMWKiCb/VWHgfUXhLQ$CFvWwloND7MkzvE1LiU5V+c5hi7+rxvYsz+j4Q3gan8'),
(39, 'l', 'l', '$argon2id$v=19$m=65536,t=3,p=4$Y+nLlgVxFofOriqZH0YAFA$C1FMh/13/vd5SyAKDffputDSjRYWH0Hyhf/gISrk3VA'),
(41, 'l', 'l2', '$argon2id$v=19$m=65536,t=3,p=4$ADrPN435yr9t0ho1dRMibQ$H4NSAIOMkBx73bKBI8Tt09XZu9sSJ2oP38YLOGJ013o'),
(42, 'l', 'l3', '$argon2id$v=19$m=65536,t=3,p=4$4rpAWLwRGOUG6iJSyfW5gA$9rcLd5q7CwIPJu7qFWZEQeg1Y/mO1ZcttKdryw1G5bY'),
(44, 'l', 'l4', '$argon2id$v=19$m=65536,t=3,p=4$QWG6ZPKjiB4jx5wuaX0oqw$RdP8QTEJezmES+2G4TG1WJoaF+dqDtyGf/B/qbtfd9w'),
(45, 'l', 'l5', '$argon2id$v=19$m=65536,t=3,p=4$YaFsNyHZA0yzFiUVBNx+XA$Z7cAHa2oOEzCI0bw0E+4/UY+4UtdByFsjGtDgxn2iVE'),
(46, 'lyes', 'lyes', '$argon2id$v=19$m=65536,t=3,p=4$0gfszYUi93INy/tKb/U/mg$flHCPoP2T9fbQcrA1zsK3XMKMHDYOLUIHCx+sOnb66A'),
(48, 'lyes', 'lyes0', '$argon2id$v=19$m=65536,t=3,p=4$/IeYJCoTuccM95z8lEr9Eg$+hkwb7bPnousB2Aq2QdLDRFQLr6unjJjsSL1JFRky1s'),
(49, 'l', 'l@g.com', '$argon2id$v=19$m=65536,t=3,p=4$21vwbNhepRfOZ/v0XebvPg$WonHDvoJ2R4POb114zVWnrriaJtYJ5FNjYe933dPPY8');

--
-- Indexes for dumped tables
--

--
-- Indexes for table `users`
--
ALTER TABLE `users` ADD FULLTEXT KEY `FullText_userName_useEmail` (`userName`,`userEmail`);
ALTER TABLE `users` ADD FULLTEXT KEY `userName` (`userName`,`userEmail`);
ALTER TABLE `users` ADD FULLTEXT KEY `userName_2` (`userName`);
ALTER TABLE `users` ADD FULLTEXT KEY `userEmail_2` (`userEmail`);

--
-- Constraints for dumped tables
--

--
-- Constraints for table `friends`
--
ALTER TABLE `friends`
  ADD CONSTRAINT `FK_user1_userID` FOREIGN KEY (`user1`) REFERENCES `users` (`userID`) ON DELETE CASCADE ON UPDATE CASCADE,
  ADD CONSTRAINT `FK_user2_userID` FOREIGN KEY (`user2`) REFERENCES `users` (`userID`) ON DELETE CASCADE ON UPDATE CASCADE;

--
-- Constraints for table `messages`
--
ALTER TABLE `messages`
  ADD CONSTRAINT `FK_reciever_userID` FOREIGN KEY (`receiver`) REFERENCES `users` (`userID`) ON DELETE CASCADE ON UPDATE CASCADE,
  ADD CONSTRAINT `FK_sender_userID` FOREIGN KEY (`sender`) REFERENCES `users` (`userID`) ON DELETE CASCADE ON UPDATE CASCADE;
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
