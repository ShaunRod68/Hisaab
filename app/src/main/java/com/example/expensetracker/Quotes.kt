package com.example.expensetracker

import java.util.Calendar

object Quotes {
    val dayList = listOf(
        // Marcus Aurelius
        "“You could leave life right now. Let that determine what you do and say and think.”\n— Marcus Aurelius",
        "“If it is not right, do not do it; if it is not true, do not say it.”\n— Marcus Aurelius",
        "“The impediment to action advances action. What stands in the way becomes the way.”\n— Marcus Aurelius",
        "“The best revenge is not to be like your enemy.”\n— Marcus Aurelius",
        "“Confine yourself to the present.”\n— Marcus Aurelius",
        "“It is not death that a man should fear, but never beginning to live.”\n— Marcus Aurelius",

        // Seneca
        "“He suffers more than necessary who suffers before it is necessary.”\n— Seneca",
        "“We are more often frightened than hurt; we suffer more from imagination than from reality.”\n— Seneca",
        "“While we are postponing, life speeds by.”\n— Seneca",
        "“Fire tests gold, suffering tests brave men.”\n— Seneca",
        "“No man is more unhappy than he who never faces adversity, for he has not been allowed to prove himself.”\n— Seneca",
        "“It is a rough road that leads to the heights of greatness.”\n— Seneca",
        "“Fate leads the willing, and drags along the reluctant.”\n— Seneca",

        // Epictetus
        "“First say to yourself what you would be; and then do what you have to do.”\n— Epictetus",
        "“If you want to improve, be content to be thought foolish and stupid.”\n— Epictetus",
        "“No man is free who is not master of himself.”\n— Epictetus",
        "“It’s not what happens to you, but how you react to it that matters.”\n— Epictetus",
        "“Some things are up to us and some are not.”\n— Epictetus",

        // Sun Tzu, The Art of War
        "“All warfare is based on deception.”\n— Sun Tzu",
        "“The supreme art of war is to subdue the enemy without fighting.”\n— Sun Tzu",
        "“If you know the enemy and know yourself, you need not fear the result of a hundred battles.”\n— Sun Tzu",
        "“Let your plans be dark and impenetrable as night, and when you move, fall like a thunderbolt.”\n— Sun Tzu",
        "“Victorious warriors win first and then go to war, while defeated warriors go to war first and then seek to win.”\n— Sun Tzu",
        "“Appear weak when you are strong, and strong when you are weak.”\n— Sun Tzu",
        "“Invincibility lies in the defense; the possibility of victory in the attack.”\n— Sun Tzu",
        "“Throw your soldiers into positions whence there is no escape, and they will prefer death to flight.”\n— Sun Tzu",

        // Warriors and strategists
        "“It is better to be feared than loved, if you cannot be both.”\n— Niccolò Machiavelli",
        "“The strong do what they can and the weak suffer what they must.”\n— Thucydides",
        "“Do nothing which is of no use.”\n— Miyamoto Musashi",
        "“Do not regret what you have done.”\n— Miyamoto Musashi",
        "“The way of the samurai is found in death.”\n— Yamamoto Tsunetomo",
        "“Molon labe” (“Come and take them”).\n— Leonidas",
        "“Alea iacta est” (“The die is cast”).\n— Julius Caesar",

        // Eastern wisdom
        "“He who conquers others is strong; he who conquers himself is mighty.”\n— Lao Tzu",
        "“You have a right to your actions, but never to your actions’ fruits.”\n— Bhagavad Gita",
        "“The journey of a thousand miles begins with a single step.”\n— Lao Tzu",
        "“He who learns but does not think is lost; he who thinks but does not learn is in great danger.”\n— Confucius",

        // Classical and popular
        "“Fortune favors the bold.”\n— Virgil",
        "“Seize the day, trusting as little as possible in tomorrow.”\n— Horace",
        "“Dripping water hollows out stone, not through force but through persistence.”\n— Ovid",
        "“The world breaks everyone, and afterward many are strong at the broken places.”\n— Ernest Hemingway",
        "“It is not the critic who counts… The credit belongs to the man who is actually in the arena.”\n— Theodore Roosevelt",
        "“We shall never surrender.”\n— Winston Churchill",
        "“Memento mori” (“Remember you must die”).\n— Roman Tradition",
        "“Know thyself.”\n— Temple of Apollo at Delphi",

        // Anime / Warriors
        "“If you don’t fight, you can’t win!”\n— Eren Yeager",
        "“The world is cruel, but also very beautiful.”\n— Mikasa Ackerman",
        "“Someone who can’t give up anything can’t change anything.”\n— Armin Arlert",
        "“Hard work is worthless for those that don’t believe in themselves.”\n— Naruto Uzumaki",
        "“People live their lives bound by what they accept as correct and true.”\n— Itachi Uchiha",
        "“Those who do not understand true pain can never understand true peace.”\n— Pain",
        "“A person grows up when they overcome hardship.”\n— Anime Proverb",

        // Extra additions from user prompt
        "“If you don’t fight, you can’t win!",
        "“The world is cruel, but also very beautiful.",
        "“Someone who can’t give up anything can’t change anything.",
        "“Hard work is worthless for those that don’t believe in themselves.",
        "“People live their lives bound by what they accept as correct and true.",
        "“Those who do not understand true pain can never understand true peace.",
        "“A person grows up when they overcome hardship."
    )

    val nightList = listOf(
        // Nietzsche
        "“God is dead. God remains dead. And we have killed him.”\n— Nietzsche",
        "“If you gaze long into an abyss, the abyss also gazes into you.”\n— Nietzsche",
        "“One must still have chaos in oneself to give birth to a dancing star.”\n— Nietzsche",
        "“Without music, life would be a mistake.”\n— Nietzsche",
        "“That which does not kill us makes us stronger.”\n— Nietzsche",
        "“He who has a why to live can bear almost any how.”\n— Nietzsche",
        "“Man would rather will nothingness than not will.”\n— Nietzsche",
        "“There are no facts, only interpretations.”\n— Nietzsche",
        "“Become who you are.”\n— Nietzsche",
        "“Whoever fights monsters should see to it that in the process he does not become a monster.”\n— Nietzsche",
        "“Madness is rare in individuals, but in groups it is the rule.”\n— Nietzsche",
        "“Convictions are more dangerous enemies of truth than lies.”\n— Nietzsche",
        "“My formula for greatness in a human being is amor fati.”\n— Nietzsche",
        "“Is man merely a mistake of God’s? Or God merely a mistake of man?”\n— Nietzsche",
        "“The snake which cannot cast its skin has to die.”\n— Nietzsche",
        "“What is the seal of liberation? No longer being ashamed in front of oneself.”\n— Nietzsche",
        "“Live dangerously! Build your cities on the slopes of Vesuvius!”\n— Nietzsche",
        "“What if a demon told you that you must live this life again, eternally?”\n— Nietzsche",

        // Kierkegaard
        "“Anxiety is the dizziness of freedom.”\n— Kierkegaard",
        "“Life can only be understood backwards; but it must be lived forwards.”\n— Kierkegaard",
        "“The self is a relation that relates itself to itself.”\n— Kierkegaard",
        "“Boredom is the root of all evil.”\n— Kierkegaard",
        "“Marry, and you will regret it; do not marry, you will also regret it.”\n— Kierkegaard",
        "“Anxiety is freedom’s possibility.”\n— Kierkegaard",
        "“The most common form of despair is not being who you are.”\n— Kierkegaard",
        "“What is a poet? An unhappy person who conceals profound anguish in his heart.”\n— Kierkegaard",
        "“People demand freedom of speech as compensation for the freedom of thought which they seldom use.”\n— Kierkegaard",
        "“The crowd is untruth.”\n— Kierkegaard",

        // Sartre, Camus, and Beauvoir
        "“Life begins on the other side of despair.”\n— Jean-Paul Sartre",
        "“The absurd is born of the confrontation between human need and the world’s unreasonable silence.”\n— Albert Camus",
        "“I rebel; therefore we exist.”\n— Albert Camus",
        "“Real generosity toward the future lies in giving all to the present.”\n— Albert Camus",
        "“Life is occupied in both perpetuating itself and surpassing itself.”\n— Simone de Beauvoir",

        // Beckett
        "“Ever tried. Ever failed. No matter. Try again. Fail again. Fail better.”\n— Beckett",
        "“Nothing is more real than nothing.”\n— Beckett",
        "“I can’t go on. I’ll go on.”\n— Beckett",
        "“Habit is a great deadener.”\n— Beckett",
        "“Nothing to be done.”\n— Beckett",
        "“We are all born mad. Some remain so.”\n— Beckett",
        "“Nothing happens, nobody comes, nobody goes, it’s awful!”\n— Beckett",
        "“We always find something, eh Didi, to give us the impression we exist?”\n— Beckett",
        "“They give birth astride of a grave.”\n— Beckett",
        "“Let us not waste our time in idle discourse!”\n— Beckett",
        "“You’re on earth. There’s no cure for that.”\n— Beckett",
        "“Nothing is funnier than unhappiness.”\n— Beckett",
        "“The end is in the beginning and yet you go on.”\n— Beckett",

        // Stoics and ancients
        "“The unexamined life is not worth living.”\n— Socrates",
        "“No man ever steps in the same river twice.”\n— Heraclitus",
        "“Death is nothing to us.”\n— Epicurus",
        "“We suffer more often in imagination than in reality.”\n— Seneca",
        "“Life is long if you know how to use it.”\n— Seneca",
        "“The universe is transformation; life is opinion.”\n— Marcus Aurelius",
        "“Very little is needed to make a happy life.”\n— Marcus Aurelius",
        "“Men are disturbed not by things, but by the views they take of them.”\n— Epictetus",
        "“Waste no more time arguing about what a good man should be. Be one.”\n— Marcus Aurelius",
        "“Do every act of your life as though it were your last.”\n— Marcus Aurelius",
        "“What stands in the way becomes the way.”\n— Marcus Aurelius",
        "“It is not that we have a short time to live, but that we waste much of it.”\n— Seneca",
        "“Begin at once to live, and count each separate day as a separate life.”\n— Seneca",
        "“Fate leads the willing, and drags along the reluctant.”\n— Seneca",
        "“Some things are up to us and some are not.”\n— Epictetus",
        "“When we exist, death is not; when death exists, we are not.”\n— Epicurus",
        "“Time is a child playing a board game.”\n— Heraclitus",

        // Early moderns and beyond
        "“To philosophize is to learn to die.”\n— Montaigne",
        "“I think, therefore I am.”\n— Descartes",
        "“Solitary, poor, nasty, brutish, and short.”\n— Hobbes",
        "“Man is born free, and everywhere he is in chains.”\n— Rousseau",
        "“We must cultivate our garden.”\n— Voltaire",
        "“What do I know?”\n— Montaigne",
        "“The heart has its reasons which reason knows nothing of.”\n— Blaise Pascal",
        "“Doubt is not a pleasant condition, but certainty is absurd.”\n— Voltaire",
        "“I am a cage, in search of a bird.”\n— Franz Kafka"
    )

    fun random(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        // After 11 PM (23:00) or before 5 AM shows night quotes, otherwise day quotes
        return if (hour >= 23 || hour < 5) {
            nightList.random()
        } else {
            dayList.random()
        }
    }
}
